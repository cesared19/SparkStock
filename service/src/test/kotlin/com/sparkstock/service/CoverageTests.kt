package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverageTests : IntegrationTestBase() {
    private lateinit var admin: String
    private lateinit var tester: String

    @BeforeEach
    fun setUp() {
        clearDevices()
        jdbc.execute("DELETE FROM coverage_targets")
        admin = login(FakeSparkyTC.admin)
        tester = login(FakeSparkyTC.tester)
    }

    private fun target(model: String, version: String?) =
        call(post("/api/v1/coverage/targets"), admin, mapOf("projectCode" to "vr", "model" to model, "version" to version))

    @Test
    fun `lists SparkyTC's projects and the models in stock`() {
        createDevice(admin, "VR headset", mapOf("system_version" to "5.2"), model = "Focus 3")
        createDevice(admin, "VR headset", mapOf("system_version" to "5.2"), model = "focus 3")
        createDevice(admin, "Laptop", model = "ThinkPad") // laptops have no coverage field
        val data = data(call(get("/api/v1/coverage/projects"), tester).andExpect(status().isOk))
        assertEquals("VR", data["projects"][0]["projectShortCode"].asText())
        assertEquals(listOf("1.1", "1.0"), data["projects"][0]["builds"].map { it.asText() })
        assertEquals(1, data["knownModels"].size())
        assertEquals("Focus 3", data["knownModels"][0]["model"].asText())
    }

    @Test
    fun `the matrix shows which targets were tested in the build and how`() {
        target("Focus 3", null).andExpect(status().isOk)
        target("XR Elite", "6.0").andExpect(status().isOk)
        target("Focus Vision", null).andExpect(status().isOk)
        target("focus 3", " ").andExpect(status().isConflict) // same as the first one
        call(post("/api/v1/coverage/targets"), tester, mapOf("projectCode" to "VR", "model" to "X")).andExpect(status().isForbidden)

        val matrix = data(call(get("/api/v1/coverage?project=VR"), tester).andExpect(status().isOk))
        assertEquals("1.1", matrix["build"].asText())
        assertEquals(3, matrix["targets"].asInt())
        assertEquals(2, matrix["covered"].asInt())
        val rows = matrix["rows"].associateBy { it["model"].asText() }

        val focus = rows.getValue("Focus 3")
        assertEquals("PASSING", focus["state"].asText())
        assertEquals(4, focus["passed"].asInt())
        assertEquals(listOf("VR-0001", "VR-0003"), focus["devices"].map { it.asText() })
        assertEquals("FAILING", rows.getValue("XR Elite")["state"].asText())
        assertEquals("MISSING", rows.getValue("Focus Vision")["state"].asText())
        // tested without being a target
        val pc = rows.getValue("Cyborg 16")
        assertNull(pc["target"])
        assertEquals("Windows 11", pc["version"].asText())
    }

    @Test
    fun `an unknown build shows every target as missing, and targets can be removed`() {
        val id = data(target("Focus 3", null))["id"].asLong()
        val matrix = data(call(get("/api/v1/coverage?project=VR&build=9.9"), tester).andExpect(status().isOk))
        assertNull(matrix["build"])
        assertEquals("MISSING", matrix["rows"][0]["state"].asText())

        call(delete("/api/v1/coverage/targets/$id"), tester).andExpect(status().isForbidden)
        call(delete("/api/v1/coverage/targets/$id"), admin).andExpect(status().isOk)
        assertEquals(0, data(call(get("/api/v1/coverage?project=VR&build=9.9"), tester))["rows"].size())
    }
}
