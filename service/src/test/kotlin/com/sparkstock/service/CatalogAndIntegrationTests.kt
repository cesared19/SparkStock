package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

class CatalogAndIntegrationTests : IntegrationTestBase() {
    private lateinit var admin: String

    @BeforeEach
    fun setUp() {
        clearDevices()
        admin = login(FakeSparkyTC.admin)
    }

    @Test
    fun `admins add device types with their own fields`() {
        val type = data(call(post("/api/v1/device-types"), admin, mapOf(
            "name" to "Base station ${System.nanoTime()}", "tagPrefix" to "bs${System.nanoTime() % 1000}", "usage" to "POOL",
            "fields" to listOf(mapOf("key" to "channel", "label" to "Channel", "kind" to "SELECT", "options" to listOf("A", "B", "B"))),
            "coverageField" to "channel",
        )).andExpect(status().isOk))
        assertEquals(listOf("A", "B"), type["fields"][0]["options"].map { it.asText() })
        assert(type["tagPrefix"].asText().startsWith("BS"))

        call(post("/api/v1/device-types"), admin, mapOf("name" to "Bad", "tagPrefix" to "BAD", "fields" to listOf(mapOf("key" to "Bad Key", "label" to "x"))))
            .andExpect(status().isBadRequest)
        call(post("/api/v1/device-types"), admin, mapOf("name" to "vr HEADSET", "tagPrefix" to "ZZ"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `locations are unique by name`() {
        call(post("/api/v1/locations"), admin, mapOf("name" to "main OFFICE")).andExpect(status().isConflict)
    }

    @Test
    fun `SparkyTC reads a tester's devices with the integration token`() {
        call(get("/api/v1/users"), admin)
        val tester = login(FakeSparkyTC.tester)
        val headset = createDevice(admin, "VR headset", mapOf("system_version" to "5.2"))
        createDevice(admin, "VR headset")
        call(post("/api/v1/devices/${headset["id"].asText()}/check-out"), tester).andExpect(status().isOk)

        call(get("/api/integration/v1/devices"), null).andExpect(status().isUnauthorized)
        call(get("/api/integration/v1/devices"), admin).andExpect(status().isUnauthorized) // a user session isn't enough

        val mine = data(call(get("/api/integration/v1/devices?usage=POOL&holderUserID=${FakeSparkyTC.tester.id}"), "test-integration-token")
            .andExpect(status().isOk))
        assertEquals(1, mine.size())
        assertEquals(headset["assetTag"].asText(), mine[0]["ref"].asText())
        assertEquals("5.2", mine[0]["version"].asText())
        assertEquals("Tom Tester", mine[0]["holderName"].asText())

        call(get("/api/integration/v1/devices/${headset["assetTag"].asText()}"), "test-integration-token")
            .andExpect(status().isOk).andExpect(jsonPath("$.data.model").value("VIVE Focus 3"))
        call(get("/api/integration/v1/devices/NOPE-1"), "test-integration-token").andExpect(status().isNotFound)
    }
}
