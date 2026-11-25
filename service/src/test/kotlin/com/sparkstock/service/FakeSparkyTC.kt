package com.sparkstock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A small SparkyTC stand-in answering the auth endpoints SparkStock uses, over real HTTP. */
object FakeSparkyTC {
    data class Account(val id: UUID, val username: String, val password: String, val displayName: String, var role: String, var active: Boolean = true)

    private val mapper = ObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    val accounts = ConcurrentHashMap<String, Account>()
    private val sessions = ConcurrentHashMap<String, String>() // token -> username
    var meCalls = 0

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { handle(it) }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    val admin = add("admin", "Ada Admin", "ADMIN")
    val tester = add("tester", "Tom Tester", "TESTER")
    val otherTester = add("other", "Olga Other", "TESTER")

    fun add(username: String, displayName: String, role: String): Account =
        Account(UUID.randomUUID(), username, "$username-pass", displayName, role).also { accounts[username] = it }

    // dates go out as milliseconds, like SparkyTC
    private fun userJson(a: Account) = mapOf("userID" to a.id, "username" to a.username, "displayName" to a.displayName,
        "role" to a.role, "isActive" to a.active, "theme" to "SYSTEM")

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
        val account = token?.let { sessions[it] }?.let { accounts[it] }?.takeIf { it.active }
        when {
            path == "/api/v1/auth/login" -> {
                val body = mapper.readTree(exchange.requestBody)
                val a = accounts[body.get("username").asText()]
                if (a == null || a.password != body.get("password").asText() || !a.active) {
                    send(exchange, 401, mapOf("status" to "ERROR", "error" to "Unauthorized", "message" to "Invalid username or password"))
                } else {
                    val newToken = UUID.randomUUID().toString()
                    sessions[newToken] = a.username
                    send(exchange, 200, mapOf("status" to "SUCCESS", "data" to mapOf(
                        "token" to newToken, "expiresAt" to Instant.now().plusSeconds(3600).toEpochMilli(), "user" to userJson(a))))
                }
            }
            account == null -> send(exchange, 401, mapOf("status" to "ERROR", "error" to "Unauthorized", "message" to "Your session has expired"))
            path == "/api/v1/auth/me" -> {
                meCalls++
                send(exchange, 200, mapOf("status" to "SUCCESS", "data" to userJson(account)))
            }
            path == "/api/v1/auth/logout" -> {
                sessions.remove(token)
                send(exchange, 200, mapOf("status" to "SUCCESS"))
            }
            path == "/api/v1/admin/users" && account.role == "ADMIN" ->
                send(exchange, 200, mapOf("status" to "SUCCESS", "data" to accounts.values.map(::userJson)))
            else -> send(exchange, 403, mapOf("status" to "ERROR", "error" to "Forbidden", "message" to "Forbidden"))
        }
    }

    private fun send(exchange: HttpExchange, status: Int, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
