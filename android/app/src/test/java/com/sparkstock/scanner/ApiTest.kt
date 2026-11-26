package com.sparkstock.scanner

import com.sparkstock.scanner.data.Api
import com.sparkstock.scanner.data.ApiException
import com.sparkstock.scanner.data.Condition
import com.sparkstock.scanner.data.OfflineException
import com.sparkstock.scanner.data.SessionExpiredException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun api(token: String? = "tok") = Api(server.url("/").toString(), token, "es-MX")

    private fun ok(data: String) = MockResponse().setBody("""{"status":"SUCCESS","data":$data}""").setHeader("Content-Type", "application/json")

    private val device = """{"id":"d1","assetTag":"VR-0001","name":"VR Headset 1","model":"Focus 3","type":{"id":1,"name":"VR headset","usage":"POOL"},"status":"AVAILABLE","isOverdue":false,"fields":{},"extra":1}"""

    @Test
    fun `signs in through SparkStock's address`() = runTest {
        server.enqueue(ok("""{"token":"abc","expiresAt":1.7E9,"user":{"userID":"u1","username":"it","displayName":"IT Manager","role":"ADMIN","isActive":true}}"""))
        val login = api(token = null).login("it", "secret")
        assertEquals("abc", login.token)
        assertTrue(login.user.isAdmin)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        assertEquals("es-MX", request.getHeader("Accept-Language"))
        assertEquals(null, request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"username\":\"it\""))
    }

    @Test
    fun `looks up a scanned tag and lends several devices`() = runTest {
        server.enqueue(ok("""{"device":$device,"events":[]}"""))
        val found = api().device("VR-0001").device
        assertTrue(found.isLendable)
        assertEquals("/api/v1/devices/by-tag/VR-0001", server.takeRequest().path)

        server.enqueue(ok("[$device]"))
        api().lend(listOf("d1", "d2"), "u2", 1_800_000_000, "for the demo")
        val request = server.takeRequest()
        assertEquals("/api/v1/devices/check-out", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"deviceIds\":[\"d1\",\"d2\"]") && body.contains("\"userId\":\"u2\"") && body.contains("\"dueAt\":1800000000"))
    }

    @Test
    fun `returns with a condition and uploads the photo`() = runTest {
        server.enqueue(ok("""{"device":$device,"events":[{"id":42,"kind":"CHECKED_IN"}]}"""))
        val detail = api().giveBack("d1", Condition.NEEDS_REPAIR, "cracked lens")
        assertTrue(server.takeRequest().body.readUtf8().contains("\"condition\":\"NEEDS_REPAIR\""))

        server.enqueue(ok("""{"photoId":7}"""))
        assertEquals(7, api().addPhoto("d1", detail.events.first().id, byteArrayOf(1, 2, 3)).photoId)
        val upload = server.takeRequest()
        assertEquals("/api/v1/devices/d1/events/42/photos", upload.path)
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
    }

    @Test
    fun `shows SparkStock's message, and says when the session expired or the server is down`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"status":"ERROR","error":"Conflict","message":"VR-0001 está prestado a Tom"}"""))
        try {
            api().device("VR-0001"); fail()
        } catch (e: ApiException) {
            assertEquals("VR-0001 está prestado a Tom", e.message)
        }
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status":"ERROR","message":"expired"}"""))
        try {
            api().me(); fail()
        } catch (e: SessionExpiredException) {
        }
        server.shutdown()
        try {
            api().me(); fail()
        } catch (e: OfflineException) {
        }
    }

    @Test
    fun `normalizes the server address`() {
        assertEquals("http://192.168.1.20:3100", Api.normalizeServer(" 192.168.1.20:3100/ "))
        assertEquals("https://stock.example.com", Api.normalizeServer("https://stock.example.com"))
        assertEquals(null, Api.normalizeServer("ftp://x"))
        assertEquals(null, Api.normalizeServer(""))
    }
}
