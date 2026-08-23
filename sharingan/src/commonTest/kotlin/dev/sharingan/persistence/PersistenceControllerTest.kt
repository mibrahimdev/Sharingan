package dev.sharingan.persistence

import dev.sharingan.HttpEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.internal.currentTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PersistenceControllerTest {

    private fun event(id: String): SharinganEvent = HttpEvent(
        id = id,
        timestampMillis = 0L,
        method = "GET",
        url = "https://api.example.com/$id",
    )

    @Test
    fun `Given a burst larger than the ring buffer When flushed Then every event is persisted`() = runBlocking {
        val total = 500
        val driver = createTestDriver()
        val store = SharinganStore(capacity = 300)
        val controller = PersistenceController(store, driver)

        val done = CompletableDeferred<Unit>()
        var flushed = 0
        controller.onBatchFlushed = { size ->
            flushed += size
            if (flushed >= total) done.complete(Unit)
        }
        controller.start()

        repeat(total) { i -> store.record(event("e$i")) }

        withTimeout(10_000) { done.await() }
        assertEquals(total, flushed)

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(total, rows.size, "all $total events should reach the DB despite a ${store.capacity} ring buffer")
        assertEquals(total, rows.map { it.id }.toSet().size, "persisted event ids must be unique")
    }

    @Test
    fun `Given many events When flushed Then they are written in batches not one per event`() = runBlocking {
        val total = 200
        val batchSize = 50
        val driver = createTestDriver()
        val store = SharinganStore(capacity = 10_000)
        val controller = PersistenceController(store, driver, batchSize = batchSize, flushIntervalMillis = 60_000)

        val batches = mutableListOf<Int>()
        val done = CompletableDeferred<Unit>()
        var flushed = 0
        controller.onBatchFlushed = { size ->
            batches += size
            flushed += size
            if (flushed >= total) done.complete(Unit)
        }
        controller.start()

        repeat(total) { i -> store.record(event("e$i")) }

        withTimeout(10_000) { done.await() }
        assertEquals(total, batches.sum())
        assertTrue(batches.size < total, "expected batching, got ${batches.size} writes for $total events")
        assertTrue(batches.all { it <= batchSize }, "no batch should exceed $batchSize, got $batches")
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
    fun `Given two sessions When events reuse a raw id Then persisted ids do not collide`() = runBlocking {
        val driver = createTestDriver()

        val store1 = SharinganStore(capacity = 10)
        val controller1 = PersistenceController(store1, driver)
        val done1 = CompletableDeferred<Unit>()
        controller1.onBatchFlushed = { done1.complete(Unit) }
        controller1.start()
        store1.record(event("http-1"))
        withTimeout(10_000) { done1.await() }

        val store2 = SharinganStore(capacity = 10)
        val controller2 = PersistenceController(store2, driver)
        val done2 = CompletableDeferred<Unit>()
        controller2.onBatchFlushed = { done2.complete(Unit) }
        controller2.start()
        store2.record(event("http-1"))
        withTimeout(10_000) { done2.await() }

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.id }.toSet().size, "cross-session event ids must be globally unique")

        val sessions = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllSessions().executeAsList()
        assertEquals(2, sessions.size)
    }

    @Test
    fun `Given a bounded channel When filled beyond capacity Then oldest are dropped and newest survive`() = runBlocking {
        val channel = newEventChannel(capacity = 4)
        (1..10).map { event("e$it") }.forEach { channel.trySend(it) }
        channel.close()

        val received = mutableListOf<SharinganEvent>()
        for (e in channel) received += e

        assertEquals(listOf("e7", "e8", "e9", "e10"), received.map { it.id })
    }

    @Test
    fun `Given buffered events When stopped Then the pending batch is drained before close`() = runBlocking {
        val driver = createTestDriver()
        val store = SharinganStore(capacity = 1_000)
        val controller = PersistenceController(store, driver, batchSize = 100, flushIntervalMillis = 60_000)
        val batches = mutableListOf<Int>()
        controller.onBatchFlushed = { size -> batches += size }
        controller.start()

        repeat(30) { i -> store.record(event("e$i")) }

        controller.stop()

        assertEquals(30, batches.sum(), "stop() must flush the in-flight batch")
    }

    @Test
    fun `Given a slow steady stream When events arrive under the batch size Then a flush lands within the interval`() = runBlocking {
        val driver = createTestDriver()
        val store = SharinganStore(capacity = 10)
        val controller = PersistenceController(store, driver, batchSize = 50, flushIntervalMillis = 250)

        var firstFlushAt = 0L
        var firstBatchSize = 0
        val flushed = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { size ->
            if (firstFlushAt == 0L) {
                firstBatchSize = size
                firstFlushAt = currentTimeMillis()
                flushed.complete(Unit)
            }
        }
        controller.start()

        val startedAt = currentTimeMillis()
        repeat(10) { i ->
            store.record(event("e$i"))
            delay(100)
        }

        withTimeout(10_000) { flushed.await() }
        val elapsed = firstFlushAt - startedAt
        assertTrue(firstBatchSize < 10, "deadline flush should fire mid-stream, got a batch of $firstBatchSize")
        assertTrue(elapsed < 600, "expected flush within interval, took $elapsed ms")

        controller.stop()
    }
}
