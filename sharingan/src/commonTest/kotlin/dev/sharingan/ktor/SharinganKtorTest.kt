package dev.sharingan.ktor

import dev.sharingan.HttpEvent
import dev.sharingan.SharinganStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class SharinganKtorTest {

    private fun client(store: SharinganStore, configure: SharinganKtorConfig.() -> Unit = {}) =
        HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/fail" -> throw RuntimeException("connect timeout")
                "/big" -> respond(
                    content = "x".repeat(100_000),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
                else -> respond(
                    content = """{"deviceId":4471,"online":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }) {
            install(SharinganKtor) {
                this.store = store
                configure()
            }
        }

    @Test
    fun `When a request succeeds, Then an HttpEvent with method url status headers and body is recorded`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store).use { http ->
            http.get("https://api.acme-iot.com/api/v2/devices/4471/state") {
                header("Accept", "application/json")
            }
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals("GET", e.method)
        assertEquals("api.acme-iot.com", e.host)
        assertEquals("/api/v2/devices/4471/state", e.path)
        assertEquals(200, e.statusCode)
        assertEquals("""{"deviceId":4471,"online":true}""", e.responseBody)
        assertEquals("application/json", e.contentType)
        assertNotNull(e.durationMillis)
        assertTrue(e.requestHeaders.any { it.first == "Accept" && it.second == "application/json" })
        assertTrue(e.responseHeaders.any { it.first.equals("Content-Type", ignoreCase = true) })
    }

    @Test
    fun `When a request carries a body, Then the request body is captured`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store).use { http ->
            http.post("https://api.acme-iot.com/api/v2/devices/4471/commands") {
                setBody(TextContent("""{"cmd":"reboot"}""", ContentType.Application.Json))
            }
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals("POST", e.method)
        assertEquals("""{"cmd":"reboot"}""", e.requestBody)
    }

    @Test
    fun `Given a redacted header, When a request is captured, Then its value is masked`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store).use { http ->
            http.get("https://api.acme-iot.com/state") {
                header("Authorization", "Bearer secret")
            }
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals("••••", e.requestHeaders.first { it.first == "Authorization" }.second)
    }

    @Test
    fun `When a request fails at transport level, Then a failure event is recorded and the exception propagates`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store).use { http ->
            assertFailsWith<RuntimeException> { http.get("https://api.acme-iot.com/fail") }
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals(null, e.statusCode)
        assertTrue(e.isFailure)
        assertNotNull(e.error)
        assertTrue("connect timeout" in e.error!!)
    }

    @Test
    fun `Given a response larger than the cap, When captured, Then the body is truncated with a marker`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store) { maxBodyBytes = 1024 }.use { http ->
            http.get("https://api.acme-iot.com/big")
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        val body = assertNotNull(e.responseBody)
        assertTrue(body.length < 100_000)
        assertTrue("truncated" in body)
    }

    @Test
    fun `Given body capture is disabled, When a request succeeds, Then bodies are omitted`() = runTest {
        val store = SharinganStore(capacity = 10)
        client(store) { captureBodies = false }.use { http ->
            http.get("https://api.acme-iot.com/state")
        }
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals(null, e.responseBody)
    }

    @Test
    fun `When the plugin captures a response, Then the caller can still read the body downstream`() = runTest {
        val store = SharinganStore(capacity = 10)
        val body = client(store).use { http ->
            http.get("https://api.acme-iot.com/state").bodyAsText()
        }
        assertEquals("""{"deviceId":4471,"online":true}""", body)
    }

    @Test
    fun `Given recording is paused, When a request succeeds, Then nothing is recorded`() = runTest {
        val store = SharinganStore(capacity = 10)
        store.setRecording(false)
        client(store).use { http ->
            http.get("https://api.acme-iot.com/state")
        }
        assertTrue(store.events.value.isEmpty())
    }
}
