package dev.sharingan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SharinganStoreTest {
    private fun event(id: String): SharinganEvent =
        MqttEvent(
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
                id = "h1",
                timestampMillis = 0L,
                method = "GET",
                url = "https://api.example.com/v1/state",
            ),
        )
        store.record(
            BleEvent(
                id = "b1",
                timestampMillis = 0L,
                operation = BleOperation.READ,
                device = "HR-Monitor",
            ),
        )
        val events = store.events.value
        assertEquals(1, events.count { it is HttpEvent })
        assertEquals(1, events.count { it is MqttEvent })
        assertEquals(1, events.count { it is BleEvent })
    }

    @Test
    fun `Given an onClear seam When the store is cleared Then the seam is invoked`() {
        val store = SharinganStore(capacity = 10)
        var cleared = 0
        store.onClear = { cleared++ }

        store.record(event("a"))
        store.clear()

        assertEquals(1, cleared)
        assertTrue(store.events.value.isEmpty())
    }

    @Test
    fun `Given many producers recording concurrently When all complete Then no event is lost or duplicated`() =
        runTest {
            val producers = 16
            val perProducer = 500
            val total = producers * perProducer

            // Capacity == total so a lost CAS update surfaces as a missing event, not legit eviction — direct check of record().
            val store = SharinganStore(capacity = total)

            // Dispatchers.Default is multi-threaded on JVM and K/N, so producers genuinely contend on the store.
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

            val expected =
                buildSet {
                    repeat(producers) { producer -> repeat(perProducer) { seq -> add("p$producer-$seq") } }
                }
            assertEquals(expected, ids.toSet())
        }

    @Test
    fun `When persistence is off Then record keeps its behavior and onRecord stays null`() {
        val store = SharinganStore(capacity = 10)
        assertNull(store.onRecord)
        store.record(event("a"))
        store.record(event("b"))
        assertEquals(listOf("a", "b"), store.events.value.map { it.id })
        assertNull(store.onRecord)
    }

    @Test
    fun `Given an onRecord seam When events are recorded Then the seam receives each event`() {
        val store = SharinganStore(capacity = 10)
        val forwarded = mutableListOf<String>()
        store.onRecord = { forwarded += it.id }

        store.record(event("a"))
        store.record(event("b"))

        assertEquals(listOf("a", "b"), store.events.value.map { it.id })
        assertEquals(listOf("a", "b"), forwarded)
    }

    @Test
    fun `Given an onRecord seam When recording is paused Then the seam receives nothing`() {
        val store = SharinganStore(capacity = 10)
        val forwarded = mutableListOf<String>()
        store.onRecord = { forwarded += it.id }
        store.setRecording(false)

        store.record(event("a"))
        store.record(event("b"))

        assertTrue(store.events.value.isEmpty())
        assertTrue(forwarded.isEmpty())
    }

    @Test
    fun `Given an onRecord seam When the buffer evicts an event Then the evicted event was still forwarded`() {
        val store = SharinganStore(capacity = 2)
        val forwarded = mutableListOf<String>()
        store.onRecord = { forwarded += it.id }

        listOf("a", "b", "c").forEach { store.record(event(it)) }

        assertEquals(listOf("b", "c"), store.events.value.map { it.id })
        assertEquals(listOf("a", "b", "c"), forwarded)
    }

}
