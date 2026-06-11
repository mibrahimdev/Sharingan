package dev.sharingan.ui

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class EventFilterTest {

    private fun http(method: String = "GET", status: Int? = 200, url: String = "https://api.acme.com/state") =
        HttpEvent(id = "h", timestampMillis = 0, method = method, url = url, statusCode = status)

    private fun mqtt(direction: MqttDirection, topic: String = "devices/4471/telemetry") =
        MqttEvent(id = "m", timestampMillis = 0, direction = direction, topic = topic)

    private fun ble(op: BleOperation, characteristic: String? = "Heart Rate Measurement") =
        BleEvent(id = "b", timestampMillis = 0, operation = op, device = "HR-9F", characteristic = characteristic)

    @Test
    fun `When events are grouped by protocol Then each event lands in its protocol tab`() {
        assertEquals(Protocol.HTTP, protocolOf(http()))
        assertEquals(Protocol.MQTT, protocolOf(mqtt(MqttDirection.PUBLISH)))
        assertEquals(Protocol.BLE, protocolOf(ble(BleOperation.READ)))
    }

    @Test
    fun `Given the errors chip When HTTP events are filtered Then only 4xx and 5xx remain`() {
        assertTrue(matchesChip(http(status = 500), "err"))
        assertTrue(matchesChip(http(status = 401), "err"))
        assertFalse(matchesChip(http(status = 200), "err"))
    }

    @Test
    fun `Given the 2xx chip When HTTP events are filtered Then only sub-300 statuses remain`() {
        assertTrue(matchesChip(http(status = 200), "2xx"))
        assertFalse(matchesChip(http(status = 304), "2xx"))
        assertFalse(matchesChip(http(status = 500), "2xx"))
    }

    @Test
    fun `Given method chips When HTTP events are filtered Then only that method remains`() {
        assertTrue(matchesChip(http(method = "GET"), "get"))
        assertFalse(matchesChip(http(method = "POST"), "get"))
        assertTrue(matchesChip(http(method = "POST"), "post"))
    }

    @Test
    fun `Given direction chips When MQTT events are filtered Then only that direction remains`() {
        assertTrue(matchesChip(mqtt(MqttDirection.PUBLISH), "pub"))
        assertFalse(matchesChip(mqtt(MqttDirection.RECEIVE), "pub"))
        assertTrue(matchesChip(mqtt(MqttDirection.RECEIVE), "recv"))
        assertTrue(matchesChip(mqtt(MqttDirection.SUBSCRIBE), "sub"))
    }

    @Test
    fun `Given BLE chips When BLE events are filtered Then notify read and errors select correctly`() {
        assertTrue(matchesChip(ble(BleOperation.NOTIFY), "notify"))
        assertFalse(matchesChip(ble(BleOperation.READ), "notify"))
        assertTrue(matchesChip(ble(BleOperation.READ), "read"))
        assertTrue(matchesChip(ble(BleOperation.ERROR), "err"))
        assertFalse(matchesChip(ble(BleOperation.NOTIFY), "err"))
    }

    @Test
    fun `Given the all chip When any event is filtered Then it always matches`() {
        assertTrue(matchesChip(http(status = 500), "all"))
        assertTrue(matchesChip(mqtt(MqttDirection.PUBLISH), "all"))
    }

    @Test
    fun `When searching Then queries match url topic characteristic and payload case-insensitively`() {
        assertTrue(matchesQuery(http(url = "https://api.acme.com/Devices/4471"), "devices"))
        assertFalse(matchesQuery(http(), "telemetry"))
        assertTrue(matchesQuery(mqtt(MqttDirection.PUBLISH, topic = "devices/4471/telemetry"), "TELEMETRY"))
        assertTrue(matchesQuery(ble(BleOperation.NOTIFY), "heart rate"))
        assertTrue(matchesQuery(http().copy(responseBody = """{"firmware":"2.4.1"}"""), "firmware"))
    }

    @Test
    fun `Given a blank query When searching Then everything matches`() {
        assertTrue(matchesQuery(http(), ""))
        assertTrue(matchesQuery(http(), "   "))
    }

    @Test
    fun `When chips for a protocol are requested Then they match the design`() {
        assertEquals(listOf("all", "err", "2xx", "get", "post"), chipsFor(Protocol.HTTP).map { it.key })
        assertEquals(listOf("all", "pub", "recv", "sub"), chipsFor(Protocol.MQTT).map { it.key })
        assertEquals(listOf("all", "notify", "read", "err"), chipsFor(Protocol.BLE).map { it.key })
    }
}
