package dev.sharingan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HttpEventTest {

    @Test
    fun `When constructed from a full URL, Then host and path are derived`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://api.acme-iot.com/api/v2/devices/4471/state",
        )
        assertEquals("api.acme-iot.com", e.host)
        assertEquals("/api/v2/devices/4471/state", e.path)
    }

    @Test
    fun `Given a URL with a query string, When path is derived, Then the query is kept`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://cdn.acme-iot.com/api/v2/firmware/manifest?ch=stable",
        )
        assertEquals("/api/v2/firmware/manifest?ch=stable", e.path)
        assertEquals("cdn.acme-iot.com", e.host)
    }

    @Test
    fun `Given a URL with a port, When host is derived, Then the port is included`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "http://localhost:8080/health",
        )
        assertEquals("localhost:8080", e.host)
        assertEquals("/health", e.path)
    }

    @Test
    fun `Given a URL with no path, When path is derived, Then it falls back to a single slash`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://example.com",
        )
        assertEquals("/", e.path)
    }

    @Test
    fun `Given a 500 response, Then the event is a failure`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://example.com/x", statusCode = 500,
        )
        assertTrue(e.isFailure)
    }

    @Test
    fun `Given a 2xx response with no transport error, Then the event is not a failure`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://example.com/x", statusCode = 200,
        )
        assertFalse(e.isFailure)
    }

    @Test
    fun `Given a transport error and no status, Then the event is a failure`() {
        val e = HttpEvent(
            id = "1", timestampMillis = 0L, method = "GET",
            url = "https://example.com/x", error = "connect timeout",
        )
        assertTrue(e.isFailure)
    }
}
