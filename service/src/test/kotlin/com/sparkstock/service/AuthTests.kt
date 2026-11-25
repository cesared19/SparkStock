package com.sparkstock.service

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

class AuthTests : IntegrationTestBase() {

    @Test
    fun `signs in with a SparkyTC account and keeps its role`() {
        val token = login(FakeSparkyTC.tester)
        call(get("/api/v1/auth/me"), token).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.username").value("tester"))
            .andExpect(jsonPath("$.data.role").value("TESTER"))
    }

    @Test
    fun `the session expiry comes from SparkyTC's milliseconds`() {
        val body = mapper.writeValueAsString(mapOf("username" to "tester", "password" to "tester-pass"))
        val result = data(mvc.perform(post("/api/v1/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)))
        val expiresIn = result["expiresAt"].asDouble() - java.time.Instant.now().epochSecond
        assert(expiresIn in 3000.0..3700.0) { "expires in $expiresIn s" }
    }

    @Test
    fun `wrong password is rejected with SparkyTC's message`() {
        call(post("/api/v1/auth/login"), null, mapOf("username" to "tester", "password" to "nope"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
    }

    @Test
    fun `requests without a valid session get 401`() {
        call(get("/api/v1/devices"), null).andExpect(status().isUnauthorized)
        call(get("/api/v1/devices"), "made-up").andExpect(status().isUnauthorized)
    }

    @Test
    fun `the SparkyTC check is cached between requests`() {
        val token = login(FakeSparkyTC.tester)
        val before = FakeSparkyTC.meCalls
        repeat(3) { call(get("/api/v1/auth/me"), token).andExpect(status().isOk) }
        assertEquals(before, FakeSparkyTC.meCalls)
    }

    @Test
    fun `logging out ends the session`() {
        val token = login(FakeSparkyTC.otherTester)
        call(post("/api/v1/auth/logout"), token).andExpect(status().isOk)
        call(get("/api/v1/auth/me"), token).andExpect(status().isUnauthorized)
    }

    @Test
    fun `admins list SparkyTC users, testers can't`() {
        call(get("/api/v1/users"), login(FakeSparkyTC.tester)).andExpect(status().isForbidden)
        val users = data(call(get("/api/v1/users"), login(FakeSparkyTC.admin)).andExpect(status().isOk))
        assert(users.any { it["username"].asText() == "other" })
    }
}
