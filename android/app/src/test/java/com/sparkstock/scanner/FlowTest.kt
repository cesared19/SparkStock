package com.sparkstock.scanner

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * The three flows of the app, on the JVM (Robolectric) against a SparkStock stand-in. There's no camera here, so
 * labels are typed in the field under the camera, which goes through the same path as a scanned QR code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()
    private val requests = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    private fun device(id: String, tag: String, name: String, status: String = "AVAILABLE", holder: String? = null) =
        """{"id":"$id","assetTag":"$tag","name":"$name","model":"Focus 3","type":{"id":1,"name":"VR headset","usage":"POOL"},""" +
            """"status":"$status","isOverdue":${holder != null},"dueAt":${if (holder != null) "1795000000" else "null"},""" +
            """"holder":${holder?.let { """{"userID":"u2","displayName":"$it","username":"maria"}""" } ?: "null"},""" +
            """"location":{"id":1,"name":"QA cabinet A","isArchived":false},"coverageVersion":"5.2"}"""

    private fun ok(data: String) = MockResponse().setBody("""{"status":"SUCCESS","data":$data}""")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.removePrefix("/api/v1/")
                requests += "${request.method} $path" to request.body.readUtf8()
                return when {
                    path == "devices/by-tag/VR-0001" -> ok("""{"device":${device("d1", "VR-0001", "VR Headset 1")},"events":[]}""")
                    path == "devices/by-tag/VR-0002" -> ok("""{"device":${device("d2", "VR-0002", "VR Headset 2", "ON_LOAN", "María Tester")},"events":[]}""")
                    path == "devices/by-tag/CONT-0001" -> ok("""{"device":${device("d3", "CONT-0001", "Controller Set 1")},"events":[]}""")
                    path == "users" -> ok("""[{"userID":"u2","username":"maria","displayName":"María Tester","role":"TESTER","isActive":true},
                        {"userID":"u3","username":"tom","displayName":"Tom Tester","role":"TESTER","isActive":true}]""")
                    path == "devices/check-out" -> ok("[${device("d1", "VR-0001", "VR Headset 1", "ON_LOAN", "María Tester")}," +
                        "${device("d3", "CONT-0001", "Controller Set 1", "ON_LOAN", "María Tester")}]")
                    path == "devices/d2/check-in" -> ok("""{"device":${device("d2", "VR-0002", "VR Headset 2", "IN_REPAIR")},"events":[{"id":99,"kind":"CHECKED_IN"}]}""")
                    path == "locations" -> ok("""[{"id":1,"name":"QA cabinet A","isArchived":false},{"id":2,"name":"Main office","isArchived":false}]""")
                    path == "inventory-checks" -> ok("""{"location":{"id":1,"name":"QA cabinet A"},"found":[${device("d1", "VR-0001", "VR Headset 1")}],
                        "missing":[${device("d4", "VR-0004", "VR Headset 4")}],"elsewhere":[{"device":${device("d3", "CONT-0001", "Controller Set 1")},"canMove":true}],
                        "unknown":["NOPE-1"],"moved":${if (request.body.toString().contains("true")) 1 else 0}}""")
                    else -> MockResponse().setResponseCode(404).setBody("""{"status":"ERROR","message":"No device has the tag ${path.substringAfterLast('/')}"}""")
                }
            }
        }
        server.start()
        // signed in as the IT manager
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sparkstock", Context.MODE_PRIVATE).edit()
            .putString("server", server.url("/").toString().trimEnd('/'))
            .putString("token", "tok")
            .putString("user", """{"userID":"u1","username":"it","displayName":"IT Manager","role":"ADMIN","isActive":true}""")
            .commit()
        compose.activityRule.scenario.recreate()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun typeTag(tag: String) {
        compose.onNode(hasText("Type a tag") and hasSetTextAction()).performTextInput(tag)
        compose.onNodeWithText("Add").performClick()
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `lends several scanned devices to a person`() {
        compose.onNodeWithText("Hi, IT Manager").assertExists()
        compose.onNodeWithText("Lend").performClick()
        typeTag("VR-0001")
        typeTag("http://10.0.0.5:3100/t/CONT-0001")
        typeTag("VR-0002")
        waitFor("Not available: on loan · with María Tester")
        waitFor("CONT-0001 · Controller Set 1")
        compose.onNodeWithText("Next").performClick()
        waitFor("Tom Tester")
        compose.onNodeWithText("María Tester").performClick()
        compose.onNode(hasText("Lend 2 devices to María Tester", substring = true)).performClick()
        waitFor("Lent to María Tester")

        val lend = requests.single { it.first == "POST devices/check-out" }.second
        assertTrue(lend, lend.contains("\"deviceIds\":[\"d1\",\"d3\"]") && lend.contains("\"userId\":\"u2\""))
    }

    @Test
    fun `receives a device that needs repair`() {
        compose.onNodeWithText("Return").performClick()
        typeTag("VR-0001")
        waitFor("VR-0001 isn't on loan")
        typeTag("VR-0002")
        waitFor("On loan to María Tester")
        compose.onNodeWithText("Needs repair").performClick()
        compose.onNode(hasText("Note (optional)") and hasSetTextAction()).performTextInput("cracked lens")
        compose.onNodeWithText("Return VR-0002").performClick()
        waitFor("VR-0002 returned and sent to repair")

        val checkIn = requests.single { it.first == "POST devices/d2/check-in" }.second
        assertTrue(checkIn, checkIn.contains("\"condition\":\"NEEDS_REPAIR\"") && checkIn.contains("cracked lens"))
    }

    @Test
    fun `checks a cabinet and moves what was elsewhere`() {
        compose.onNodeWithText("Inventory check").performClick()
        waitFor("QA cabinet A")
        compose.onNodeWithText("QA cabinet A").performClick()
        typeTag("VR-0001")
        typeTag("CONT-0001")
        typeTag("NOPE-1")
        compose.onNodeWithText("Finish (3)").performClick()
        waitFor("Missing (1)")
        compose.onNodeWithText("Not in SparkStock (1)").assertExists()
        compose.onNodeWithText("Move 1 here").performClick()
        waitFor("Moved 1 devices to QA cabinet A.")
        val checks = requests.filter { it.first == "POST inventory-checks" }.map { it.second }
        assertTrue(checks.toString(), checks.size == 2 && checks[0].contains("\"apply\":false") && checks[1].contains("\"apply\":true"))
        assertTrue(checks[0].contains("\"assetTags\":[\"NOPE-1\",\"CONT-0001\",\"VR-0001\"]"))
    }
}
