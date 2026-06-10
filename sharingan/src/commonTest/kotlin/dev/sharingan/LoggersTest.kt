package dev.sharingan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class LoggersTest {

    @Test
    fun `When an MQTT publish is logged, Then a PUBLISH event with topic qos and payload is stored`() {
        val store = SharinganStore(capacity = 10)
        val mqtt = MqttLogger(store)
        mqtt.publish(topic = "devices/4471/telemetry", payload = """{"temp":23.4}""", qos = 1, retained = false)
        val e = assertIs<MqttEvent>(store.events.value.single())
        assertEquals(MqttDirection.PUBLISH, e.direction)
        assertEquals("devices/4471/telemetry", e.topic)
        assertEquals(1, e.qos)
        assertEquals("""{"temp":23.4}""", e.payload)
        assertEquals(13L, e.payloadSizeBytes)
    }

    @Test
    fun `When an MQTT receive and subscribe are logged, Then their directions are RECEIVE and SUBSCRIBE`() {
        val store = SharinganStore(capacity = 10)
        val mqtt = MqttLogger(store)
        mqtt.received(topic = "devices/4471/commands/reboot", payload = "{}", qos = 1)
        mqtt.subscribed(topicFilter = "devices/4471/commands/#", qos = 1)
        val dirs = store.events.value.filterIsInstance<MqttEvent>().map { it.direction }
        assertEquals(listOf(MqttDirection.RECEIVE, MqttDirection.SUBSCRIBE), dirs)
    }

    @Test
    fun `When a BLE notify is logged, Then a NOTIFY event with device characteristic and uuid is stored`() {
        val store = SharinganStore(capacity = 10)
        val ble = BleLogger(store)
        ble.notify(
            device = "HR-Monitor-9F",
            characteristic = "Heart Rate Measurement",
            uuid = "0x2A37",
            value = """{"bpm":72}""",
        )
        val e = assertIs<BleEvent>(store.events.value.single())
        assertEquals(BleOperation.NOTIFY, e.operation)
        assertEquals("HR-Monitor-9F", e.device)
        assertEquals("Heart Rate Measurement", e.characteristic)
        assertEquals("0x2A37", e.uuid)
    }

    @Test
    fun `When a BLE error is logged, Then the event is a failure with an ERROR operation`() {
        val store = SharinganStore(capacity = 10)
        val ble = BleLogger(store)
        ble.error(device = "HR-Monitor-9F", characteristic = "Body Sensor Location", uuid = "0x2A38", message = "Attribute not found")
        val e = assertIs<BleEvent>(store.events.value.single())
        assertEquals(BleOperation.ERROR, e.operation)
        assertTrue(e.isFailure)
        assertEquals("Attribute not found", e.error)
    }

    @Test
    fun `When an HTTP call is logged manually, Then redacted headers are masked`() {
        val store = SharinganStore(capacity = 10)
        val http = HttpLogger(store, redactedHeaders = setOf("Authorization"))
        http.log(
            method = "GET",
            url = "https://api.acme-iot.com/api/v2/devices/4471/state",
            statusCode = 200,
            durationMillis = 142,
            requestHeaders = listOf("Authorization" to "Bearer secret-token", "Accept" to "application/json"),
        )
        val e = assertIs<HttpEvent>(store.events.value.single())
        assertEquals("••••", e.requestHeaders.first { it.first == "Authorization" }.second)
        assertEquals("application/json", e.requestHeaders.first { it.first == "Accept" }.second)
    }

    @Test
    fun `When two events are logged, Then their generated ids differ`() {
        val store = SharinganStore(capacity = 10)
        val mqtt = MqttLogger(store)
        mqtt.publish(topic = "a", payload = null)
        mqtt.publish(topic = "a", payload = null)
        val (e1, e2) = store.events.value
        assertNotEquals(e1.id, e2.id)
    }
}
