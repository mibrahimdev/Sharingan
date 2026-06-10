package dev.sharingan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SharinganStoreTest {

    private fun event(id: String): SharinganEvent = MqttEvent(
        id = id,
        timestampMillis = 0L,
        direction = MqttDirection.PUBLISH,
        topic = "t",
        qos = 0,
        retained = false,
        payload = null,
        payloadSizeBytes = null,
    )

    @Test
    fun `When an event is recorded, Then it appears in the events flow in insertion order`() {
        val store = SharinganStore(capacity = 10)
        store.record(event("a"))
        store.record(event("b"))
        assertEquals(listOf("a", "b"), store.events.value.map { it.id })
    }

    @Test
    fun `Given a full buffer, When another event is recorded, Then the oldest event is evicted`() {
        val store = SharinganStore(capacity = 3)
        listOf("a", "b", "c", "d").forEach { store.record(event(it)) }
        assertEquals(listOf("b", "c", "d"), store.events.value.map { it.id })
    }

    @Test
    fun `Given recording is paused, When an event is recorded, Then it is dropped`() {
        val store = SharinganStore(capacity = 10)
        store.setRecording(false)
        store.record(event("a"))
        assertTrue(store.events.value.isEmpty())
        assertFalse(store.isRecording.value)
    }

    @Test
    fun `Given recording was paused, When recording resumes, Then new events are captured again`() {
        val store = SharinganStore(capacity = 10)
        store.setRecording(false)
        store.record(event("dropped"))
        store.setRecording(true)
        store.record(event("kept"))
        assertEquals(listOf("kept"), store.events.value.map { it.id })
    }

    @Test
    fun `When the store is cleared, Then the events flow becomes empty`() {
        val store = SharinganStore(capacity = 10)
        store.record(event("a"))
        store.clear()
        assertTrue(store.events.value.isEmpty())
    }

    @Test
    fun `When events of each protocol are recorded, Then counts reflect each protocol`() {
        val store = SharinganStore(capacity = 10)
        store.record(event("m1"))
        store.record(
            HttpEvent(
                id = "h1", timestampMillis = 0L, method = "GET",
                url = "https://api.example.com/v1/state",
            )
        )
        store.record(
            BleEvent(
                id = "b1", timestampMillis = 0L, operation = BleOperation.READ,
                device = "HR-Monitor",
            )
        )
        val events = store.events.value
        assertEquals(1, events.count { it is HttpEvent })
        assertEquals(1, events.count { it is MqttEvent })
        assertEquals(1, events.count { it is BleEvent })
    }
}
