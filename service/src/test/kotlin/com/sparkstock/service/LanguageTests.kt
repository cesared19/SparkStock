package com.sparkstock.service

import com.sparkstock.service.services.ReminderService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageTests : IntegrationTestBase() {
    @Autowired lateinit var reminders: ReminderService

    @BeforeEach
    fun setUp() {
        clearDevices()
        FakeSparkyTC.notifications.clear()
        jdbc.update("UPDATE users SET language = 'EN'")
    }

    @Test
    fun `messages follow Accept-Language`() {
        val admin = login(FakeSparkyTC.admin)
        val vr = typeId(admin, "VR headset")
        call(post("/api/v1/devices").header(HttpHeaders.ACCEPT_LANGUAGE, "es-MX,es;q=0.9"), admin, mapOf("typeId" to vr, "model" to " "))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Escribe el modelo"))
        call(post("/api/v1/devices").header(HttpHeaders.ACCEPT_LANGUAGE, "en"), admin, mapOf("typeId" to vr, "model" to " "))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Enter the model"))
        call(post("/api/v1/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "es"), null, mapOf("username" to "tester", "password" to "nope"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"))
        call(get("/api/v1/devices").header(HttpHeaders.ACCEPT_LANGUAGE, "es"), "nope")
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("Tu sesión expiró, vuelve a entrar"))
    }

    @Test
    fun `reminders come in the language each person last used, and long overdue loans reach the admins`() {
        val admin = login(FakeSparkyTC.admin)
        call(get("/api/v1/users"), admin).andExpect(status().isOk)
        val tester = login(FakeSparkyTC.tester)
        // the tester uses SparkStock in Spanish
        call(get("/api/v1/auth/me").header(HttpHeaders.ACCEPT_LANGUAGE, "es"), tester).andExpect(status().isOk)
        assertEquals("ES", jdbc.queryForObject("SELECT language FROM users WHERE username = 'tester'", String::class.java))
        // requests without a language (e.g. other apps) don't change it
        call(get("/api/v1/auth/me"), tester).andExpect(status().isOk)
        assertEquals("ES", jdbc.queryForObject("SELECT language FROM users WHERE username = 'tester'", String::class.java))

        val device = createDevice(admin, "VR headset")
        call(post("/api/v1/devices/${device["id"].asText()}/check-out"), tester).andExpect(status().isOk)
        jdbc.update("UPDATE devices SET due_at = now() - interval '5 days' WHERE id = ?::uuid", device["id"].asText())
        reminders.sendReminders()

        val toTester = FakeSparkyTC.notifications.values.single { it["userID"] == FakeSparkyTC.tester.id.toString() }
        assertTrue((toTester["title"] as String).startsWith("Devuelve ${device["assetTag"].asText()}: vencía el"), toTester["title"] as String)
        val escalation = FakeSparkyTC.notifications.filterKeys { it.contains("sparkstock:escalation:") }.values
        assertEquals(setOf(FakeSparkyTC.admin.id.toString()), escalation.map { it["userID"] }.toSet())
        assertEquals("${device["assetTag"].asText()} hasn't been returned: 5 days late", escalation.single()["title"])
    }
}
