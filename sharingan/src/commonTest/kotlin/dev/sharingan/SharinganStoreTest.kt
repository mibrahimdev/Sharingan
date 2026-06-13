package dev.sharingan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
    fun `When an event is recorded Then it appears in the events flow in insertion order`() {
        val store = SharinganStore(capacity = 10)
        store.record(event("a"))
        store.record(event("b"))
        assertEquals(listOf("a", "b"), store.events.value.map { it.id })
    }

    @Test
    fun `Given a full buffer When another event is recorded Then the oldest event is evicted`() {
        val store = SharinganStore(capacity = 3)
        listOf("a", "b", "c", "d").forEach { store.record(event(it)) }
        assertEquals(listOf("b", "c", "d"), store.events.value.map { it.id })
    }

    @Test
    fun `Given recording is paused When an event is recorded Then it is dropped`() {
        val store = SharinganStore(capacity = 10)
        store.setRecording(false)
        store.record(event("a"))
        assertTrue(store.events.value.isEmpty())
        assertFalse(store.isRecording.value)
    }

    @Test
    fun `Given recording was paused When recording resumes Then new events are captured again`() {
        val store = SharinganStore(capacity = 10)
        store.setRecording(false)
        store.record(event("dropped"))
        store.setRecording(true)
        store.record(event("kept"))
        assertEquals(listOf("kept"), store.events.value.map { it.id })
    }

    @Test
    fun `When the store is cleared Then the events flow becomes empty`() {
        val store = SharinganStore(capacity = 10)
        store.record(event("a"))
        store.clear()
        assertTrue(store.events.value.isEmpty())
    }

    @Test
    fun `When events of each protocol are recorded Then counts reflect each protocol`() {
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

    @Test
    fun `Given many producers recording concurrently When all complete Then no event is lost or duplicated`() = runTest {
        val producers = 16
        val perProducer = 500
        val total = producers * perProducer

        // Capacity holds every event, so a lost compare-and-set update surfaces
        // as a missing event rather than as legitimate ring-buffer eviction —
        // making this a direct check of the lock-free `record` path under load.
        val store = SharinganStore(capacity = total)

        // Each producer records on a real background thread (Dispatchers.Default
        // is multi-threaded on both the JVM and Kotlin/Native), so the producers
        // genuinely contend on the same MutableStateFlow rather than interleaving
        // cooperatively on a single test thread.
        coroutineScope {
            repeat(producers) { producer ->
                launch(Dispatchers.Default) {
                    repeat(perProducer) { seq -> store.record(event("p$producer-$seq")) }
                }
            }
        }

        val ids = store.events.value.map { it.id }
        assertEquals(total, ids.size, "lost events: a concurrent record() dropped an update")
        assertEquals(total, ids.toSet().size, "duplicate event ids in the store")

        val expected = buildSet {
            repeat(producers) { producer -> repeat(perProducer) { seq -> add("p$producer-$seq") } }
        }
        assertEquals(expected, ids.toSet())
    }
}
