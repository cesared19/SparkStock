package com.sparkstock.service

import com.sparkstock.service.services.ReminderService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTests : IntegrationTestBase() {
    @Autowired lateinit var reminders: ReminderService

    private lateinit var admin: String
    private lateinit var tester: String

    @BeforeEach
    fun setUp() {
        clearDevices()
        FakeSparkyTC.notifications.clear()
        admin = login(FakeSparkyTC.admin)
        tester = login(FakeSparkyTC.tester)
        call(get("/api/v1/users"), admin).andExpect(status().isOk) // copies the accounts, admins included
    }

    private fun sent(prefix: String) = FakeSparkyTC.notifications.filterKeys { it.substringAfter('|').startsWith(prefix) }.values

    @Test
    fun `reminds borrowers of overdue and due loans, and admins of warranties, once each`() {
        val overdue = createDevice(admin, "VR headset", serial = "SN-A")
        val dueSoon = createDevice(admin, "VR headset", serial = "SN-B")
        val laptop = data(call(post("/api/v1/devices"), admin, mapOf(
            "typeId" to typeId(admin, "Laptop"), "model" to "ThinkPad", "warrantyEnd" to LocalDate.now().plusDays(10).toString(),
        )))["device"]
        call(post("/api/v1/devices/${overdue["id"].asText()}/check-out"), tester).andExpect(status().isOk)
        call(post("/api/v1/devices/${dueSoon["id"].asText()}/check-out"), tester).andExpect(status().isOk)
        jdbc.update("UPDATE devices SET due_at = now() - interval '2 days' WHERE id = ?::uuid", overdue["id"].asText())
        jdbc.update("UPDATE devices SET due_at = now() + interval '5 hours' WHERE id = ?::uuid", dueSoon["id"].asText())

        val first = data(call(post("/api/v1/reminders/send"), admin).andExpect(status().isOk))
        assertEquals(0, first["failed"].asInt())

        val overdueNote = sent("sparkstock:overdue:${overdue["assetTag"].asText()}").single()
        assertEquals(FakeSparkyTC.tester.id.toString(), overdueNote["userID"])
        assertEquals("SparkStock", overdueNote["app"])
        assertTrue((overdueNote["title"] as String).startsWith("Return ${overdue["assetTag"].asText()}: it was due on"))
        assertEquals(1, sent("sparkstock:due-soon:${dueSoon["assetTag"].asText()}").size)

        val warranty = sent("sparkstock:warranty:${laptop["assetTag"].asText()}")
        assertEquals(setOf(FakeSparkyTC.admin.id.toString()), warranty.map { it["userID"] }.toSet())

        // running again the same day sends nothing new
        val again = data(call(post("/api/v1/reminders/send"), admin))
        assertEquals(0, again["sent"].asInt())
        assertEquals(first["sent"].asInt(), again["alreadySent"].asInt())
    }

    @Test
    fun `returned and on-time loans get no reminder, and testers can't trigger them`() {
        val device = createDevice(admin, "VR headset")
        call(post("/api/v1/devices/${device["id"].asText()}/check-out"), tester).andExpect(status().isOk)
        call(post("/api/v1/reminders/send"), tester).andExpect(status().isForbidden)
        assertEquals(0, reminders.sendReminders().sent)
        assertTrue(FakeSparkyTC.notifications.isEmpty())
    }
}
