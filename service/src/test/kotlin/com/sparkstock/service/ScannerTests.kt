package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the IT manager's scanner app does: lend to people, several at once, returns with photos, inventory checks. */
class ScannerTests : IntegrationTestBase() {
    private lateinit var admin: String
    private lateinit var tester: String

    // the smallest valid JPEG header is enough for the check
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    @BeforeEach
    fun setUp() {
        clearDevices()
        jdbc.execute("DELETE FROM event_photos")
        admin = login(FakeSparkyTC.admin)
        tester = login(FakeSparkyTC.tester)
        call(get("/api/v1/users"), admin).andExpect(status().isOk)
    }

    @Test
    fun `an admin lends several devices to someone at once, all or nothing`() {
        val headset = createDevice(admin, "VR headset")["id"].asText()
        val controllers = createDevice(admin, "VR headset", model = "Focus 3 controllers")["id"].asText()
        val busy = createDevice(admin, "VR headset", model = "XR Elite")["id"].asText()
        call(post("/api/v1/devices/$busy/check-out"), tester).andExpect(status().isOk)

        // one of them is taken: nothing is lent
        call(post("/api/v1/devices/check-out"), admin, mapOf("deviceIds" to listOf(headset, controllers, busy), "userId" to FakeSparkyTC.otherTester.id))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Tom Tester")))
        assertEquals("AVAILABLE", data(call(get("/api/v1/devices/$headset"), admin))["device"]["status"].asText())

        val lent = data(call(post("/api/v1/devices/check-out"), admin, mapOf("deviceIds" to listOf(headset, controllers), "userId" to FakeSparkyTC.otherTester.id))
            .andExpect(status().isOk))
        assertEquals(2, lent.size())
        assertTrue(lent.all { it["holder"]["username"].asText() == "other" && it["status"].asText() == "ON_LOAN" })
        // the history says who lent it
        val event = data(call(get("/api/v1/devices/$headset"), admin))["events"][0]
        assertEquals("CHECKED_OUT", event["kind"].asText())
        assertEquals("admin", event["actor"]["username"].asText())
        assertEquals("other", event["holder"]["username"].asText())
    }

    @Test
    fun `only admins lend to someone else`() {
        val headset = createDevice(admin, "VR headset")["id"].asText()
        call(post("/api/v1/devices/$headset/check-out"), tester, mapOf("userId" to FakeSparkyTC.otherTester.id))
            .andExpect(status().isForbidden)
        call(post("/api/v1/devices/check-out").header(HttpHeaders.ACCEPT_LANGUAGE, "es"), admin, mapOf("deviceIds" to emptyList<String>()))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Escanea al menos un dispositivo"))
    }

    @Test
    fun `a device that comes back broken goes to repair, with a photo`() {
        val headset = createDevice(admin, "VR headset")["id"].asText()
        call(post("/api/v1/devices/$headset/check-out"), admin, mapOf("userId" to FakeSparkyTC.tester.id)).andExpect(status().isOk)
        val back = data(call(post("/api/v1/devices/$headset/check-in"), admin, mapOf("condition" to "NEEDS_REPAIR", "note" to "cracked lens"))
            .andExpect(status().isOk))
        assertEquals("IN_REPAIR", back["device"]["status"].asText())
        val event = back["events"][0]
        assertEquals("CHECKED_IN", event["kind"].asText())
        assertEquals("NEEDS_REPAIR", event["details"]["condition"].asText())
        assertEquals("tester", event["holder"]["username"].asText())

        val photo = MockMultipartFile("file", "lens.jpg", "image/jpeg", jpeg)
        val url = "/api/v1/devices/$headset/events/${event["id"].asLong()}/photos"
        call(multipart(url).file(MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image".toByteArray())), admin)
            .andExpect(status().isBadRequest)
        call(multipart(url).file(photo), tester).andExpect(status().isForbidden) // the tester didn't record the return
        val photoId = data(call(multipart(url).file(photo), admin).andExpect(status().isOk))["photoId"].asLong()

        val history = data(call(get("/api/v1/devices/$headset"), tester))["events"][0]
        assertEquals(listOf(photoId), history["photoIds"].map { it.asLong() })
        call(get("/api/v1/event-photos/$photoId"), tester).andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(jpeg))

        // the report counts the loan as returned
        assertEquals(1, data(call(get("/api/v1/reports"), admin))["loans"]["returned"].asInt())
    }

    @Test
    fun `an inventory check finds what's missing and what's somewhere else, and moves it`() {
        val cabinet = data(call(post("/api/v1/locations"), admin, mapOf("name" to "Cabinet ${System.nanoTime()}")))["id"].asLong()
        val main = jdbc.queryForObject("SELECT id FROM locations WHERE name = 'Main office'", Long::class.java)!!
        fun device(model: String, location: Long) = data(call(post("/api/v1/devices"), admin, mapOf(
            "typeId" to typeId(admin, "VR headset"), "model" to model, "locationId" to location)))["device"]
        val here = device("Focus 3", cabinet)
        val missing = device("XR Elite", cabinet)
        val misplaced = device("Focus Vision", main)
        val onLoan = device("Focus 3", main)
        call(post("/api/v1/devices/${onLoan["id"].asText()}/check-out"), tester).andExpect(status().isOk)

        val tags = listOf(here, misplaced, onLoan).map { it["assetTag"].asText().lowercase() } + "NOPE-99"
        call(post("/api/v1/inventory-checks"), tester, mapOf("locationId" to cabinet, "assetTags" to tags)).andExpect(status().isForbidden)

        val check = data(call(post("/api/v1/inventory-checks"), admin, mapOf("locationId" to cabinet, "assetTags" to tags)).andExpect(status().isOk))
        assertEquals(listOf(here["id"].asText()), check["found"].map { it["id"].asText() })
        assertEquals(listOf(missing["id"].asText()), check["missing"].map { it["id"].asText() })
        val elsewhere = check["elsewhere"].associate { it["device"]["id"].asText() to it["canMove"].asBoolean() }
        assertEquals(mapOf(misplaced["id"].asText() to true, onLoan["id"].asText() to false), elsewhere)
        assertEquals(listOf("NOPE-99"), check["unknown"].map { it.asText() })
        assertEquals(0, check["moved"].asInt())

        val applied = data(call(post("/api/v1/inventory-checks"), admin, mapOf("locationId" to cabinet, "assetTags" to tags, "apply" to true)))
        assertEquals(1, applied["moved"].asInt())
        val moved = data(call(get("/api/v1/devices/${misplaced["id"].asText()}"), admin))
        assertEquals(cabinet, moved["device"]["location"]["id"].asLong())
        assertEquals("Main office", moved["events"][0]["details"]["from"].asText())
        // the loan stays as it was
        assertEquals("ON_LOAN", data(call(get("/api/v1/devices/${onLoan["id"].asText()}"), admin))["device"]["status"].asText())
    }
}
