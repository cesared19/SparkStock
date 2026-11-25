package com.sparkstock.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rows as the client maps them from the Confluence "Testing Devices" export. */
class ImportTests : IntegrationTestBase() {
    private lateinit var admin: String

    private val rows = listOf(
        row(1, "000001", "VR Headset 1", "Focus 3", "FA5BL3N00418", "SKQ1.230810.001 dev-keys", "2x Vibe controller, Battery Pack", "VR Headset", "VIVE"),
        row(2, "000002", "VR Headset 2", "Focus 3", "FA5AR3N00416", "SKQ1.230810.001 dev-keys", "2x Vibe controller, Battery Pack", "VR Headset", "VIVE"),
        row(3, "000004", "VR Laptop Test", "Cyborg 16", "V2508N0028203", "Windows 11", "Networking Device for Headset sync", "PC", "MSI"),
        row(4, "000005", "VR Laptop Live", "Katana 15", "V2508N0017076", "Windows 11", "N/A", "PC", "MSI"),
        row(5, "000007", "VR Controller Set 1", "Focus 3 controllers", "FA 59Y2K002092005-09 + FA 5APSK0027622025-10", "N/A", "N/A", "Controller", "VIVE"),
        row(6, "000010", "VR Network Adapter Test", "AC750 WI-FI Travel Router", "Y259028000148", "N/A", "Network Cable, Micro-USB Power Cable", "Network Adapter", "tp-link"),
    )

    private fun row(line: Int, id: String, name: String, model: String, serial: String, firmware: String, attachments: String, type: String, brand: String) =
        mapOf("line" to line, "externalId" to id, "name" to name, "model" to model, "serialNumber" to serial, "version" to firmware,
            "accessories" to attachments, "typeName" to type, "manufacturer" to brand, "projectCode" to "VR")

    @BeforeEach
    fun setUp() {
        clearDevices()
        jdbc.execute("DELETE FROM device_types WHERE name IN ('PC', 'Controller', 'Network Adapter')")
        admin = login(FakeSparkyTC.admin)
    }

    private fun import(rows: List<Map<String, Any?>>, dryRun: Boolean) =
        data(call(post("/api/v1/import/devices"), admin, mapOf("rows" to rows, "dryRun" to dryRun)).andExpect(status().isOk))

    private fun devices() = data(call(get("/api/v1/devices"), admin))

    @Test
    fun `the preview shows what would happen and saves nothing`() {
        val preview = import(rows, dryRun = true)
        assertFalse(preview["applied"].asBoolean())
        assertEquals(6, preview["created"].asInt())
        assertEquals(listOf("PC", "Controller", "Network Adapter"), preview["newTypes"].map { it.asText() })
        assertEquals(0, devices().size())
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM device_types WHERE name = 'PC'", Int::class.java))
    }

    @Test
    fun `importing maps the Confluence columns and creates the missing types`() {
        val result = import(rows, dryRun = false)
        assertTrue(result["applied"].asBoolean())
        val all = devices().associateBy { it["externalId"].asText() }

        val headset = all.getValue("000001")
        assertEquals("VR headset", headset["type"]["name"].asText()) // matched the existing type, ignoring case
        assertEquals("SKQ1.230810.001 dev-keys", headset["fields"]["system_version"].asText())
        assertEquals("2x Vibe controller, Battery Pack", headset["fields"]["accessories"].asText())
        assertEquals("VIVE", headset["manufacturer"].asText())
        assertEquals("VR", headset["projectCode"].asText())
        assertEquals("AVAILABLE", headset["status"].asText())

        val laptop = all.getValue("000005")
        assertEquals("PC", laptop["type"]["name"].asText())
        assertEquals("POOL", laptop["type"]["usage"].asText())
        assertEquals("Windows 11", laptop["coverageVersion"].asText())
        assertTrue(laptop["fields"]["accessories"] == null) // N/A is empty
        assertTrue(laptop["assetTag"].asText().startsWith("PC-"))

        assertTrue(all.getValue("000010")["assetTag"].asText().startsWith("NA-"))
        assertTrue(all.getValue("000007")["assetTag"].asText().startsWith("CONT-"))
    }

    @Test
    fun `importing the same file again changes nothing, and edited rows update`() {
        import(rows, dryRun = false)
        val again = import(rows, dryRun = true)
        assertEquals(6, again["unchanged"].asInt())

        val edited = rows.map { if (it["externalId"] == "000001") it + ("version" to "SKQ1.240101.002") else it }
        val result = import(edited, dryRun = false)
        assertEquals(1, result["updated"].asInt())
        assertEquals(6, devices().size())
        val headset = devices().first { it["externalId"].asText() == "000001" }
        assertEquals("SKQ1.240101.002", headset["fields"]["system_version"].asText())
    }

    @Test
    fun `a bad row stops the whole import`() {
        val broken = rows + mapOf("line" to 7, "externalId" to "000099", "name" to null, "model" to null, "typeName" to "PC")
        val result = import(broken, dryRun = false)
        assertFalse(result["applied"].asBoolean())
        assertEquals(1, result["errors"].asInt())
        assertEquals("ERROR", result["rows"][6]["action"].asText())
        assertEquals(0, devices().size())
    }

    @Test
    fun `testers can't import`() {
        call(post("/api/v1/import/devices"), login(FakeSparkyTC.tester), mapOf("rows" to rows)).andExpect(status().isForbidden)
    }
}
