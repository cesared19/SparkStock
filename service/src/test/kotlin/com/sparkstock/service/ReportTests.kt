package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportTests : IntegrationTestBase() {
    private lateinit var admin: String
    private lateinit var tester: String

    @BeforeEach
    fun setUp() {
        clearDevices()
        admin = login(FakeSparkyTC.admin)
        tester = login(FakeSparkyTC.tester)
    }

    /** Moves a device's loan into the past: checked out `daysAgo`, due `dueDaysAgo` (negative = in the future). */
    private fun backdate(id: String, kind: String, daysAgo: Double) =
        jdbc.update("UPDATE device_events SET created_at = now() - make_interval(secs => ?) WHERE device_id = ?::uuid AND kind = ?",
            daysAgo * 86400, id, kind)

    @Test
    fun `stock, loan time, use and disuse and alerts`() {
        val overdue = createDevice(admin, "VR headset", model = "Focus 3")["id"].asText()
        val returned = createDevice(admin, "VR headset", model = "XR Elite")["id"].asText()
        val idle = createDevice(admin, "VR headset", model = "Focus Vision")["id"].asText()
        val dueSoon = createDevice(admin, "VR headset", model = "Focus 3")["id"].asText()
        createDevice(admin, "Laptop", model = "ThinkPad")
        jdbc.update("UPDATE devices SET created_at = now() - interval '60 days'")
        jdbc.update("UPDATE device_events SET created_at = now() - interval '60 days' WHERE kind = 'CREATED'")

        // on loan for 10 days, asked for 3: very overdue
        call(post("/api/v1/devices/$overdue/check-out"), tester).andExpect(status().isOk)
        backdate(overdue, "CHECKED_OUT", 10.0)
        jdbc.update("UPDATE devices SET held_since = now() - interval '10 days', due_at = now() - interval '7 days' WHERE id = ?::uuid", overdue)

        // lent 20 days ago for 5 days and returned late
        call(post("/api/v1/devices/$returned/check-out"), tester).andExpect(status().isOk)
        call(post("/api/v1/devices/$returned/check-in"), tester).andExpect(status().isOk)
        backdate(returned, "CHECKED_OUT", 20.0)
        backdate(returned, "CHECKED_IN", 15.0)
        jdbc.update("UPDATE device_events SET details = '{\"overdue\": true}' WHERE device_id = ?::uuid AND kind = 'CHECKED_IN'", returned)

        // due in 5 hours: attention
        call(post("/api/v1/devices/$dueSoon/check-out"), tester).andExpect(status().isOk)
        jdbc.update("UPDATE devices SET due_at = now() + interval '5 hours' WHERE id = ?::uuid", dueSoon)

        val report = data(call(get("/api/v1/reports?period=30"), tester).andExpect(status().isOk))

        val stock = report["stock"]
        assertEquals(5, stock["total"].asInt())
        assertEquals(2, stock["byStatus"]["ON_LOAN"].asInt())
        assertEquals("VR headset", stock["byType"][0]["name"].asText())
        assertEquals(4, stock["byType"][0]["total"].asInt())

        val loans = report["loans"]
        assertEquals(3, loans["started"].asInt())
        assertEquals(1, loans["returned"].asInt())
        assertEquals(1, loans["returnedLate"].asInt())
        assertEquals(2, loans["activeNow"].asInt())
        assertEquals(120.0, loans["averageHours"].asDouble(), 0.5)
        assertEquals(overdue, loans["longest"]["device"]["id"].asText())
        assertTrue(loans["longest"]["active"].asBoolean())
        assertEquals("tester", loans["byPerson"][0]["person"]["username"].asText())
        assertEquals(1, loans["byPerson"][0]["returnedLate"].asInt())

        val usage = report["usage"]
        assertEquals(4, usage["devices"].size()) // lent devices only
        assertEquals(overdue, usage["devices"][0]["device"]["id"].asText())
        assertEquals(0.333, usage["devices"][0]["utilization"].asDouble(), 0.01)
        val idleRow = usage["devices"].first { it["device"]["id"].asText() == idle }
        assertTrue(idleRow["unused"].asBoolean())
        assertEquals(1, usage["unused"].asInt())

        val alerts = report["alerts"]
        assertEquals(listOf("VERY_OVERDUE", "ATTENTION"), alerts["alerts"].map { it["level"].asText() })
        assertEquals(overdue, alerts["alerts"][0]["device"]["id"].asText())
        assertEquals(3.33, alerts["alerts"][0]["ratio"].asDouble(), 0.05)
        assertEquals(3, alerts["escalateAfterDays"].asInt())

        // in a 30-day window the loan from 20 days ago counts; everything since the start also does
        assertEquals(3, data(call(get("/api/v1/reports?period=all"), tester))["loans"]["started"].asInt())
        call(get("/api/v1/reports?period=7"), tester).andExpect(status().isBadRequest)
    }
}
