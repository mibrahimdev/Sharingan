package dev.sharingan

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SharinganExportTest {

    private val httpEvent = HttpEvent(
        id = "h1",
        timestampMillis = 0L,
        method = "GET",
        url = "https://api.acme-iot.com/api/v2/devices/4471/state",
        statusCode = 200,
        durationMillis = 142,
        requestHeaders = listOf("Authorization" to "••••", "Accept" to "application/json"),
        responseHeaders = listOf("Content-Type" to "application/json"),
        responseBody = """{"deviceId":4471,"online":true}""",
        contentType = "application/json",
        responseSizeBytes = 4300,
    )

    private val mqttEvent = MqttEvent(
        id = "m1",
        timestampMillis = 0L,
        direction = MqttDirection.PUBLISH,
        topic = "devices/4471/telemetry",
        qos = 1,
        retained = false,
        payload = """{"temp":23.4}""",
        payloadSizeBytes = 13,
    )

    private val bleEvent = BleEvent(
        id = "b1",
        timestampMillis = 0L,
        operation = BleOperation.NOTIFY,
        device = "HR-Monitor-9F",
        characteristic = "Heart Rate Measurement",
        uuid = "0x2A37",
        payload = """{"bpm":72}""",
        sizeBytes = 8,
    )

    // ── agent markdown: single event ─────────────────────────────

    @Test
    fun `When an HTTP event is exported as agent markdown, Then it has heading status host headers and body fence`() {
        val md = SharinganExport.agentMarkdown(httpEvent)
        assertContains(md, "## GET /api/v2/devices/4471/state")
        assertContains(md, "**Status:** 200")
        assertContains(md, "142ms")
        assertContains(md, "**Host:** api.acme-iot.com")
        assertContains(md, "- Authorization: ••••")
        assertContains(md, "```json")
        assertContains(md, """"deviceId":4471""")
    }

    @Test
    fun `When an MQTT event is exported as agent markdown, Then it has direction topic qos and payload`() {
        val md = SharinganExport.agentMarkdown(mqttEvent)
        assertContains(md, "## MQTT PUB devices/4471/telemetry")
        assertContains(md, "**QoS:** 1")
        assertContains(md, "**Retained:** false")
        assertContains(md, """"temp":23.4""")
    }

    @Test
    fun `When a BLE event is exported as agent markdown, Then it has operation characteristic device and uuid`() {
        val md = SharinganExport.agentMarkdown(bleEvent)
        assertContains(md, "## BLE NOTIFY Heart Rate Measurement")
        assertContains(md, "**Device:** HR-Monitor-9F")
        assertContains(md, "**UUID:** 0x2A37")
        assertContains(md, """"bpm":72""")
    }

    @Test
    fun `Given a failed HTTP event, When exported as agent markdown, Then the error is stated`() {
        val md = SharinganExport.agentMarkdown(httpEvent.copy(statusCode = null, error = "connect timeout"))
        assertContains(md, "**Error:** connect timeout")
    }

    // ── agent markdown: session ──────────────────────────────────

    @Test
    fun `When a session is exported as agent markdown, Then it has a header with counts and one section per event`() {
        val md = SharinganExport.agentMarkdown(listOf(httpEvent, mqttEvent, bleEvent))
        assertContains(md, "# Sharingan session export")
        assertContains(md, "3 events")
        assertContains(md, "HTTP 1 · MQTT 1 · BLE 1")
        assertContains(md, "## GET /api/v2/devices/4471/state")
        assertContains(md, "## MQTT PUB devices/4471/telemetry")
        assertContains(md, "## BLE NOTIFY Heart Rate Measurement")
    }

    // ── cURL ─────────────────────────────────────────────────────

    @Test
    fun `When an HTTP event is exported as cURL, Then method url and headers are present`() {
        val curl = SharinganExport.curl(httpEvent)
        assertContains(curl, "curl -X GET")
        assertContains(curl, "'https://api.acme-iot.com/api/v2/devices/4471/state'")
        assertContains(curl, "-H 'Accept: application/json'")
        assertFalse("--data" in curl)
    }

    @Test
    fun `Given a request body, When exported as cURL, Then a data flag carries the body`() {
        val curl = SharinganExport.curl(
            httpEvent.copy(method = "POST", requestBody = """{"cmd":"reboot"}""")
        )
        assertContains(curl, "curl -X POST")
        assertContains(curl, "--data '{\"cmd\":\"reboot\"}'")
    }

    @Test
    fun `Given a body with single quotes, When exported as cURL, Then the quotes are shell-escaped`() {
        val curl = SharinganExport.curl(httpEvent.copy(method = "POST", requestBody = "it's"))
        assertContains(curl, """--data 'it'\''s'""")
    }

    // ── JSON ─────────────────────────────────────────────────────

    @Test
    fun `When an HTTP event is exported as JSON, Then protocol fields are present and strings are escaped`() {
        val json = SharinganExport.json(httpEvent.copy(responseBody = "line1\n\"quoted\""))
        assertContains(json, "\"protocol\": \"http\"")
        assertContains(json, "\"method\": \"GET\"")
        assertContains(json, "\"statusCode\": 200")
        assertContains(json, "line1\\n\\\"quoted\\\"")
    }

    @Test
    fun `When a session is exported as JSON, Then it wraps all events with tool metadata`() {
        val json = SharinganExport.sessionJson(listOf(httpEvent, mqttEvent, bleEvent))
        assertContains(json, "\"tool\": \"sharingan\"")
        assertContains(json, "\"events\": [")
        assertContains(json, "\"protocol\": \"mqtt\"")
        assertContains(json, "\"protocol\": \"ble\"")
    }

    // ── byte formatting ──────────────────────────────────────────

    @Test
    fun `When byte counts are formatted, Then they render like the design rows`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("312 B", formatBytes(312))
        assertEquals("4.2 KB", formatBytes(4300))
        assertEquals("1.1 MB", formatBytes(1_150_000))
    }

    @Test
    fun `Given no byte count, When formatted, Then a dash placeholder is returned`() {
        assertEquals("—", formatBytes(null))
    }

    @Test
    fun `When a session summary is exported, Then it lists one line per event with outcome`() {
        val text = SharinganExport.summary(listOf(httpEvent, mqttEvent, bleEvent))
        assertTrue(text.lines().any { it.contains("GET /api/v2/devices/4471/state") && it.contains("200") })
        assertTrue(text.lines().any { it.contains("PUB devices/4471/telemetry") })
        assertTrue(text.lines().any { it.contains("NOTIFY Heart Rate Measurement") })
    }
}
