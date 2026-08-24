package dev.sharingan.internal

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.HttpLogger
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import kotlin.test.Test
import kotlin.test.assertTrue

internal class EventDtoTest {

    @Test
    fun `Given an HTTP event When encoded Then method url status and redacted header are present`() {
        val event = HttpEvent(
            id = "http-1",
            timestampMillis = 1_700_000_000_000L,
            method = "POST",
            url = "https://api.example.com/v1/users",
            statusCode = 201,
            durationMillis = 42L,
            requestHeaders = listOf("Authorization" to HttpLogger.REDACTED_VALUE, "Content-Type" to "application/json"),
            responseHeaders = listOf("X-Request-Id" to "abc"),
            requestBody = """{"name":"Ada"}""",
            responseBody = """{"id":1}""",
            contentType = "application/json",
            responseSizeBytes = 128L,
        )

        val dto = EventDto.fromEvent(event)
        val encoded = json.encodeToString(EventDto.serializer(), dto)

        assertTrue("\"method\":\"POST\"" in encoded)
        assertTrue("\"url\":\"https://api.example.com/v1/users\"" in encoded)
        assertTrue("\"statusCode\":201" in encoded)
        assertTrue("\"Authorization\"" in encoded)
        assertTrue("\"value\":\"${HttpLogger.REDACTED_VALUE}\"" in encoded)
        assertTrue("\"Content-Type\"" in encoded)
        assertTrue("\"application/json\"" in encoded)
    }

    @Test
    fun `Given an MQTT publish event When encoded Then topic direction qos and retained flag are present`() {
        val event = MqttEvent(
            id = "mqtt-1",
            timestampMillis = 1_700_000_000_000L,
            direction = MqttDirection.PUBLISH,
            topic = "devices/001/status",
            qos = 1,
            retained = true,
            payload = """{"online":true}""",
            payloadSizeBytes = 64L,
        )

        val dto = EventDto.fromEvent(event)
        val encoded = json.encodeToString(EventDto.serializer(), dto)

        assertTrue("\"direction\":\"PUBLISH\"" in encoded)
        assertTrue("\"topic\":\"devices/001/status\"" in encoded)
        assertTrue("\"qos\":1" in encoded)
        assertTrue("\"retained\":true" in encoded)
        assertTrue("\"payload\":\"{\\\"online\\\":true}\"" in encoded)
    }

    @Test
    fun `Given an MQTT receive event When encoded Then direction is RECEIVE`() {
        val event = MqttEvent(
            id = "mqtt-2",
            timestampMillis = 1_700_000_000_001L,
            direction = MqttDirection.RECEIVE,
            topic = "alerts/critical",
            qos = 2,
            retained = false,
            payload = "alert",
        )

        val dto = EventDto.fromEvent(event)
        val encoded = json.encodeToString(EventDto.serializer(), dto)

        assertTrue("\"direction\":\"RECEIVE\"" in encoded)
        assertTrue("\"topic\":\"alerts/critical\"" in encoded)
        assertTrue("\"qos\":2" in encoded)
    }

    @Test
    fun `Given a BLE read event When encoded Then operation device and characteristic are present`() {
        val event = BleEvent(
            id = "ble-1",
            timestampMillis = 1_700_000_000_000L,
            operation = BleOperation.READ,
            device = "HR-Monitor-A1",
            characteristic = "Heart Rate Measurement",
            uuid = "00002a37-0000-1000-8000-00805f9b34fb",
            payload = "{\"bpm\":72}",
            sizeBytes = 32L,
        )

        val dto = EventDto.fromEvent(event)
        val encoded = json.encodeToString(EventDto.serializer(), dto)

        assertTrue("\"operation\":\"READ\"" in encoded)
        assertTrue("\"device\":\"HR-Monitor-A1\"" in encoded)
        assertTrue("\"characteristic\":\"Heart Rate Measurement\"" in encoded)
        assertTrue("\"uuid\":\"00002a37-0000-1000-8000-00805f9b34fb\"" in encoded)
    }

    @Test
    fun `Given a BLE error event When encoded Then error flag is present`() {
        val event = BleEvent(
            id = "ble-2",
            timestampMillis = 1_700_000_000_001L,
            operation = BleOperation.CONNECT,
            device = "Scale-B2",
            error = "GATT 133",
        )

        val dto = EventDto.fromEvent(event)
        val encoded = json.encodeToString(EventDto.serializer(), dto)

        assertTrue("\"operation\":\"CONNECT\"" in encoded)
        assertTrue("\"device\":\"Scale-B2\"" in encoded)
        assertTrue("\"error\":\"GATT 133\"" in encoded)
    }
}
