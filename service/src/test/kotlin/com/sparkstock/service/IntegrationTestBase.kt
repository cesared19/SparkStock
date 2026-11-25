package com.sparkstock.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
abstract class IntegrationTestBase {
    companion object {
        // one database for every test class, started once
        private val postgres = PostgreSQLContainer<Nothing>("postgres:16").apply {
            withDatabaseName("sparkstock")
            withUsername("test")
            withPassword("test")
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("sparkstock.sparkytc.base-url") { FakeSparkyTC.baseUrl }
        }
    }

    @Autowired lateinit var context: WebApplicationContext
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(context).build() }

    /** Removes the devices so each test starts from the seeded catalog. */
    fun clearDevices() {
        jdbc.execute("DELETE FROM device_events")
        jdbc.execute("DELETE FROM devices")
    }

    fun login(account: FakeSparkyTC.Account): String {
        val body = mapper.writeValueAsString(mapOf("username" to account.username, "password" to account.password))
        val result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andReturn()
        return mapper.readTree(result.response.contentAsString)["data"]["token"].asText()
    }

    fun call(request: MockHttpServletRequestBuilder, token: String?, body: Any? = null): ResultActions {
        token?.let { request.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        body?.let { request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(it)) }
        return mvc.perform(request)
    }

    fun data(actions: ResultActions): JsonNode = mapper.readTree(actions.andReturn().response.contentAsString)["data"]

    fun typeId(token: String, name: String): Long =
        data(call(get("/api/v1/device-types"), token).andExpect(status().isOk)).first { it["name"].asText() == name }["id"].asLong()

    fun createDevice(token: String, type: String, fields: Map<String, Any?> = emptyMap(), serial: String? = null, model: String = "VIVE Focus 3"): JsonNode =
        data(call(post("/api/v1/devices"), token, mapOf(
            "typeId" to typeId(token, type), "model" to model, "manufacturer" to "HTC", "serialNumber" to serial, "fields" to fields,
        )).andExpect(status().isOk))["device"]
}
