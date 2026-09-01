package dev.sharingan.internal

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PersistenceToRowTest {
    @Test
    fun `Given an HTTP event with bodies When it becomes a row Then the bodies are not persisted`() {
        val event =
            HttpEvent(
                id = "http-1",
                timestampMillis = 1_700_000_000_000L,
                method = "POST",
                url = "https://api.example.com/v1/users",
                statusCode = 201,
                requestBody = """{"name":"Ada"}""",
                responseBody = """{"id":1}""",
            )

        val dto = json.decodeFromString(EventDto.serializer(), toRow(event).payloadJson)

        assertTrue(dto is HttpDto)
        assertNull(dto.requestBody, "request bodies must stay off disk (persistBodies = false)")
        assertNull(dto.responseBody, "response bodies must stay off disk (persistBodies = false)")
        assertEquals("POST", dto.method)
    }

    @Test
    fun `Given an MQTT event with a payload When it becomes a row Then the payload is not persisted`() {
        val event =
            MqttEvent(
                id = "mqtt-1",
                timestampMillis = 1_700_000_000_000L,
                direction = MqttDirection.PUBLISH,
                topic = "devices/001/status",
                payload = """{"online":true}""",
            )

        val dto = json.decodeFromString(EventDto.serializer(), toRow(event).payloadJson)

        assertTrue(dto is MqttDto)
        assertNull(dto.payload, "MQTT payloads must stay off disk (persistBodies = false)")
        assertEquals("devices/001/status", dto.topic)
    }

    @Test
    fun `Given a BLE event with a payload When it becomes a row Then the payload is not persisted`() {
        val event =
            BleEvent(
                id = "ble-1",
                timestampMillis = 1_700_000_000_000L,
                operation = BleOperation.READ,
                device = "HR-Monitor-A1",
                payload = "{\"bpm\":72}",
            )

        val dto = json.decodeFromString(EventDto.serializer(), toRow(event).payloadJson)

        assertTrue(dto is BleDto)
        assertNull(dto.payload, "BLE payloads must stay off disk (persistBodies = false)")
        assertEquals("HR-Monitor-A1", dto.device)
    }
}
