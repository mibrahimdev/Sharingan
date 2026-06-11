package dev.sharingan.internal

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class NotificationContentTest {

    private val http = HttpEvent(
        id = "h1",
        timestampMillis = 0,
        method = "GET",
        url = "https://api.acme.com/state",
        statusCode = 200,
    )
    private val mqtt = MqttEvent(
        id = "m1",
        timestampMillis = 1,
        direction = MqttDirection.PUBLISH,
        topic = "devices/4471/telemetry",
    )
    private val ble = BleEvent(
        id = "b1",
        timestampMillis = 2,
        operation = BleOperation.NOTIFY,
        device = "HR-9F",
        characteristic = "Heart Rate Measurement",
    )

    @Test
    fun `When there are no events Then there is nothing to post`() {
        assertNull(notificationContentOf(emptyList(), recording = true))
    }

    @Test
    fun `Given recording When content is built Then title shows Capturing with the event count and action is Pause`() {
        val content = assertNotNull(notificationContentOf(listOf(http, mqtt), recording = true))
        assertEquals("Sharingan — Capturing · 2 events", content.title)
        assertEquals("Pause", content.actionLabel)
    }

    @Test
    fun `Given paused When content is built Then title shows Paused and action is Resume`() {
        val content = assertNotNull(notificationContentOf(listOf(http), recording = false))
        assertEquals("Sharingan — Paused · 1 events", content.title)
        assertEquals("Resume", content.actionLabel)
    }

    @Test
    fun `When content is built Then the counters line counts per protocol`() {
        val content = assertNotNull(notificationContentOf(listOf(http, http, mqtt, ble), recording = true))
        assertEquals("HTTP 2 · MQTT 1 · BLE 1", content.countsLine)
    }

    @Test
    fun `When content is built Then the expanded text shows the last three events newest first`() {
        val older = http.copy(id = "h0", url = "https://api.acme.com/older")
        val content = assertNotNull(notificationContentOf(listOf(older, http, mqtt, ble), recording = true))
        assertEquals(
            "HTTP 2 · MQTT 1 · BLE 1\n" +
                "NOTIFY Heart Rate Measurement\n" +
                "PUB devices/4471/telemetry\n" +
                "GET /state → 200",
            content.expandedText,
        )
    }
}
