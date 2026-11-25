package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceTests : IntegrationTestBase() {
    private lateinit var admin: String
    private lateinit var tester: String
    private lateinit var other: String

    @BeforeEach
    fun setUp() {
        clearDevices()
        admin = login(FakeSparkyTC.admin)
        tester = login(FakeSparkyTC.tester)
        other = login(FakeSparkyTC.otherTester)
    }

    @Test
    fun `admins create devices with the next tag of their type and normalized fields`() {
        val first = createDevice(admin, "VR headset", mapOf("system_version" to " 5.2.1 ", "mdm" to "vive business+", "unknown" to "x"))
        val second = createDevice(admin, "VR headset")
        assertTrue(first["assetTag"].asText().matches(Regex("VR-\\d{4}")))
        assertEquals(first["assetTag"].asText().removePrefix("VR-").toInt() + 1, second["assetTag"].asText().removePrefix("VR-").toInt())
        assertEquals("5.2.1", first["fields"]["system_version"].asText())
        assertEquals("VIVE Business+", first["fields"]["mdm"].asText())
        assertTrue(first["fields"]["unknown"] == null)
        assertEquals("5.2.1", first["coverageVersion"].asText())
        assertEquals("AVAILABLE", first["status"].asText())
    }

    @Test
    fun `invalid field values and duplicate serials are rejected`() {
        val vr = typeId(admin, "VR headset")
        call(post("/api/v1/devices"), admin, mapOf("typeId" to vr, "model" to "Focus 3", "fields" to mapOf("battery_health" to "Great")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Battery health must be one of: Good, Fair, Poor"))
        createDevice(admin, "VR headset", serial = "SN-1")
        call(post("/api/v1/devices"), admin, mapOf("typeId" to vr, "model" to "Focus 3", "serialNumber" to "sn-1"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `testers can't create or edit devices`() {
        call(post("/api/v1/devices"), tester, mapOf("typeId" to typeId(tester, "VR headset"), "model" to "x"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a tester checks out a headset for 7 days and returns it`() {
        val id = createDevice(admin, "VR headset")["id"].asText()
        val out = data(call(post("/api/v1/devices/$id/check-out"), tester).andExpect(status().isOk))
        assertEquals("ON_LOAN", out["device"]["status"].asText())
        assertEquals("tester", out["device"]["holder"]["username"].asText())
        val due = Instant.ofEpochMilli((out["device"]["dueAt"].asDouble() * 1000).toLong())
        val days = ChronoUnit.HOURS.between(Instant.now(), due) / 24.0
        assertTrue(days in 6.9..7.1, "due in $days days")

        // someone else can't take it nor return it
        call(post("/api/v1/devices/$id/check-out"), other).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("on loan to Tom Tester")))
        call(post("/api/v1/devices/$id/check-in"), other).andExpect(status().isForbidden)

        val mine = data(call(get("/api/v1/devices?holder=me"), tester))
        assertEquals(1, mine.size())

        val back = data(call(post("/api/v1/devices/$id/check-in"), tester, mapOf("note" to "all good")).andExpect(status().isOk))
        assertEquals("AVAILABLE", back["device"]["status"].asText())
        assertEquals(listOf("CHECKED_IN", "CHECKED_OUT", "CREATED"), back["events"].map { it["kind"].asText() })
        assertEquals("all good", back["events"][0]["note"].asText())
        assertEquals("tester", back["events"][0]["holder"]["username"].asText())
    }

    @Test
    fun `loan dates must be in the future and within the maximum`() {
        val id = createDevice(admin, "VR headset")["id"].asText()
        call(post("/api/v1/devices/$id/check-out"), tester, mapOf("dueAt" to Instant.now().minusSeconds(60).epochSecond))
            .andExpect(status().isBadRequest)
        call(post("/api/v1/devices/$id/check-out"), tester, mapOf("dueAt" to Instant.now().plus(200, ChronoUnit.DAYS).epochSecond))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `overdue loans show on the dashboard`() {
        val id = createDevice(admin, "VR headset")["id"].asText()
        call(post("/api/v1/devices/$id/check-out"), tester).andExpect(status().isOk)
        jdbc.update("UPDATE devices SET due_at = now() - interval '1 day' WHERE id = ?::uuid", id)
        val dashboard = data(call(get("/api/v1/dashboard"), other).andExpect(status().isOk))
        assertEquals(id, dashboard["overdue"][0]["id"].asText())
        assertTrue(dashboard["overdue"][0]["isOverdue"].asBoolean())
        assertEquals(7, dashboard["loanDefaultDays"].asInt())
    }

    @Test
    fun `laptops are assigned by admins, not lent`() {
        call(get("/api/v1/users"), admin).andExpect(status().isOk) // copies the SparkyTC accounts
        val id = createDevice(admin, "Laptop", mapOf("ram_gb" to "16"), model = "ThinkPad T14")["id"].asText()
        call(post("/api/v1/devices/$id/check-out"), tester).andExpect(status().isConflict)
        call(post("/api/v1/devices/$id/assign"), tester, mapOf("userId" to FakeSparkyTC.tester.id)).andExpect(status().isForbidden)

        val assigned = data(call(post("/api/v1/devices/$id/assign"), admin, mapOf("userId" to FakeSparkyTC.tester.id)).andExpect(status().isOk))
        assertEquals("ASSIGNED", assigned["device"]["status"].asText())
        assertEquals(16.0, assigned["device"]["fields"]["ram_gb"].asDouble())

        val moved = data(call(post("/api/v1/devices/$id/assign"), admin, mapOf("userId" to FakeSparkyTC.otherTester.id)).andExpect(status().isOk))
        assertEquals("other", moved["device"]["holder"]["username"].asText())
        assertEquals(listOf("ASSIGNED", "UNASSIGNED", "ASSIGNED", "CREATED"), moved["events"].map { it["kind"].asText() })

        call(post("/api/v1/devices/$id/unassign"), admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.device.status").value("AVAILABLE"))
    }

    @Test
    fun `sending a loaned device to repair ends the loan and retired devices are hidden`() {
        val id = createDevice(admin, "VR headset")["id"].asText()
        call(post("/api/v1/devices/$id/check-out"), tester).andExpect(status().isOk)
        val repair = data(call(post("/api/v1/devices/$id/status"), admin, mapOf("status" to "IN_REPAIR", "note" to "cracked lens")).andExpect(status().isOk))
        assertEquals("IN_REPAIR", repair["device"]["status"].asText())
        assertTrue(repair["device"]["holder"] == null)
        assertEquals("tester", repair["events"][0]["holder"]["username"].asText())

        call(post("/api/v1/devices/$id/status"), admin, mapOf("status" to "ON_LOAN")).andExpect(status().isBadRequest)
        call(post("/api/v1/devices/$id/status"), admin, mapOf("status" to "RETIRED")).andExpect(status().isOk)
        assertEquals(0, data(call(get("/api/v1/devices"), tester)).size())
        assertEquals(1, data(call(get("/api/v1/devices?includeRetired=true"), tester)).size())
    }

    @Test
    fun `editing records what changed and a scanned tag opens the device`() {
        val device = createDevice(admin, "VR headset", mapOf("system_version" to "5.0"))
        val id = device["id"].asText()
        val edited = data(call(put("/api/v1/devices/$id"), admin, mapOf(
            "model" to "VIVE Focus 3", "manufacturer" to "HTC", "fields" to mapOf("system_version" to "5.1"),
        )).andExpect(status().isOk))
        assertEquals("UPDATED", edited["events"][0]["kind"].asText())
        assertEquals(listOf("fields.system_version"), edited["events"][0]["details"]["changed"].map { it.asText() })

        call(get("/api/v1/devices/by-tag/${device["assetTag"].asText().lowercase()}"), tester).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.device.id").value(id))
    }

    @Test
    fun `search matches tag, model, holder and field values`() {
        createDevice(admin, "VR headset", mapOf("system_version" to "5.2"), model = "VIVE XR Elite")
        createDevice(admin, "VR headset", model = "VIVE Focus 3")
        assertEquals(1, data(call(get("/api/v1/devices?q=elite"), tester)).size())
        assertEquals(1, data(call(get("/api/v1/devices?q=vive 5.2"), tester)).size())
        assertEquals(2, data(call(get("/api/v1/devices?q=vive"), tester)).size())
    }
}
