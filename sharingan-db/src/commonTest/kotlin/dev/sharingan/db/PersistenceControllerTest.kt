package dev.sharingan.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class PersistenceControllerTest {

    private fun event(id: String): EventRow = EventRow(
        rawId = id,
        timestampMillis = 0L,
        type = "HTTP",
        isFailure = false,
        hostOrTopic = "api.example.com",
        payloadJson = """{"method":"GET"}""",
    )

    @Test
    fun `Given a burst within the channel capacity When flushed Then every event is persisted`() = runBlocking {
        val total = 500
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(toRow = { it }, driver = driver)

        val done = CompletableDeferred<Unit>()
        var flushed = 0
        controller.onBatchFlushed = { size ->
            flushed += size
            if (flushed >= total) done.complete(Unit)
        }
        controller.start()

        repeat(total) { i -> controller.submit(event("e$i")) }

        withTimeout(10_000) { done.await() }
        assertEquals(total, flushed)

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(total, rows.size, "all $total events should reach the DB")
        assertEquals(total, rows.map { it.id }.toSet().size, "persisted event ids must be unique")

        controller.close()
    }

    @Test
    fun `Given a burst beyond the channel capacity When flushed Then the newest events survive`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 4,
            flushIntervalMillis = 60_000,
            channelCapacity = 4,
        )

        fun row(id: String) = EventRow(
            rawId = id,
            timestampMillis = 0L,
            type = "HTTP",
            isFailure = false,
            hostOrTopic = "api.example.com",
            payloadJson = """{"id":"$id"}""",
        )

        // Submit before start() so the flusher cannot drain the channel while
        // the burst is being written; DROP_OLDEST must retain the newest 4.
        repeat(10) { i -> controller.submit(row("e$i")) }

        val done = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { done.complete(Unit) }
        controller.start()

        withTimeout(10_000) { done.await() }

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(4, rows.size, "channel should keep only the newest 4 events")
        assertEquals(
            setOf("""{"id":"e6"}""", """{"id":"e7"}""", """{"id":"e8"}""", """{"id":"e9"}"""),
            rows.map { it.payload_json }.toSet(),
        )

        controller.close()
    }

    @Test
    fun `Given a toRow mapper that throws on one event When flushed Then the flusher survives and other events persist`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { row ->
                if (row.rawId == "boom") error("mapper failure")
                row
            },
            driver = driver,
            batchSize = 1,
            flushIntervalMillis = 60_000,
        )
        controller.start()

        fun row(id: String) = EventRow(
            rawId = id,
            timestampMillis = 0L,
            type = "HTTP",
            isFailure = false,
            hostOrTopic = "api.example.com",
            payloadJson = """{"id":"$id"}""",
        )

        controller.submit(row("before"))
        controller.submit(row("boom"))
        controller.submit(row("after"))

        controller.stop()

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(2, rows.size)
        assertEquals(setOf("""{"id":"before"}""", """{"id":"after"}"""), rows.map { it.payload_json }.toSet())

        controller.close()
    }

    @Test
    fun `Given many events When flushed Then they are written in batches not one per event`() = runBlocking {
        val total = 200
        val batchSize = 50
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = batchSize,
            flushIntervalMillis = 60_000,
        )

        val batches = mutableListOf<Int>()
        val done = CompletableDeferred<Unit>()
        var flushed = 0
        controller.onBatchFlushed = { size ->
            batches += size
            flushed += size
            if (flushed >= total) done.complete(Unit)
        }
        controller.start()

        repeat(total) { i -> controller.submit(event("e$i")) }

        withTimeout(10_000) { done.await() }
        assertEquals(total, batches.sum())
        assertTrue(batches.size < total, "expected batching, got ${batches.size} writes for $total events")
        assertTrue(batches.all { it <= batchSize }, "no batch should exceed $batchSize, got $batches")

        controller.close()
    }

    @Test
    fun `Given two sessions When events reuse a raw id Then persisted ids do not collide`() = runBlocking {
        val driver = createTestDriver()

        val controller1 = PersistenceController<EventRow>(toRow = { it }, driver = driver)
        val done1 = CompletableDeferred<Unit>()
        controller1.onBatchFlushed = { done1.complete(Unit) }
        controller1.start()
        controller1.submit(event("http-1"))
        withTimeout(10_000) { done1.await() }
        controller1.stop()

        val controller2 = PersistenceController<EventRow>(toRow = { it }, driver = driver)
        val done2 = CompletableDeferred<Unit>()
        controller2.onBatchFlushed = { done2.complete(Unit) }
        controller2.start()
        controller2.submit(event("http-1"))
        withTimeout(10_000) { done2.await() }
        controller2.stop()

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.id }.toSet().size, "cross-session event ids must be globally unique")

        val sessions = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllSessions().executeAsList()
        assertEquals(2, sessions.size)

        controller1.close()
        controller2.close()
    }

    @Test
    fun `Given a pending batch When clear is called Then no events are resurrected`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 100,
            // Short deadline so flushed-1 lands; the pending events are queued
            // behind ClearAll microseconds later, far ahead of the next deadline.
            flushIntervalMillis = 100,
        )
        val firstFlush = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { firstFlush.complete(Unit) }
        controller.start()

        controller.submit(event("flushed-1"))
        withTimeout(10_000) { firstFlush.await() }

        // Queued but never flushed: under the batch size and far from the deadline.
        controller.submit(event("pending-1"))
        controller.submit(event("pending-2"))

        val cleared = CompletableDeferred<Unit>()
        controller.onCleared = { cleared.complete(Unit) }
        controller.clear()
        withTimeout(10_000) { cleared.await() }

        // Drain the flusher: a resurrection would commit the pending batch now.
        controller.stop()

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(0, rows.size, "clear must delete persisted rows and discard the pending batch")

        controller.close()
    }

    @Test
    fun `Given a bounded channel When filled beyond capacity Then oldest are dropped and newest survive`() = runBlocking {
        val channel = newEventChannel<EventRow>(capacity = 4)
        (1..10).map { event("e$it") }.forEach { channel.trySend(it) }
        channel.close()

        val received = mutableListOf<EventRow>()
        for (e in channel) received += e

        assertEquals(listOf("e7", "e8", "e9", "e10"), received.map { it.rawId })
    }

    @Test
    fun `Given stop is called twice Then it is idempotent and does not crash`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 100,
            flushIntervalMillis = 60_000,
        )
        controller.start()
        repeat(10) { i -> controller.submit(event("e$i")) }

        controller.stop()
        controller.stop()

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(10, rows.size)

        controller.close()
    }

    @Test
    fun `Given start is called twice without stop Then only one flusher runs and every event is persisted`() = runBlocking {
        val total = 5
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = total,
            flushIntervalMillis = 60_000,
        )

        val flushed = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { flushed.complete(Unit) }

        controller.start()
        controller.start()

        repeat(total) { i -> controller.submit(event("e$i")) }

        withTimeout(10_000) { flushed.await() }

        assertEquals(1, controller.flusherStartCount(), "only one flusher coroutine must start")

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(total, rows.size, "no event may be lost to a cancelled loser flusher")

        val sessions = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllSessions().executeAsList()
        assertEquals(1, sessions.size, "only one flusher/session must be created")

        controller.close()
    }

    @Test
    fun `Given stop has been called When start is called again Then it throws IllegalStateException`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(toRow = { it }, driver = driver)
        controller.start()
        controller.submit(event("a"))
        controller.stop()

        assertFailsWith<IllegalStateException> {
            controller.start()
        }

        controller.close()
    }

    @Test
    fun `Given buffered events When stopped Then the pending batch is drained before close`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 100,
            flushIntervalMillis = 60_000,
        )
        val batches = mutableListOf<Int>()
        controller.onBatchFlushed = { size -> batches += size }
        controller.start()

        repeat(30) { i -> controller.submit(event("e$i")) }

        controller.stop()

        assertEquals(30, batches.sum(), "stop() must flush the in-flight batch")

        controller.close()
    }

    @Test
    fun `Given events under the batch size When the deadline passes Then a deadline flush lands`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 50,
            flushIntervalMillis = 250,
        )
        val flushed = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { flushed.complete(Unit) }
        controller.start()

        controller.submit(event("a"))
        controller.submit(event("b"))

        withTimeout(10_000) { flushed.await() }

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(2, rows.size)

        controller.close()
    }

    @Test
    fun `Given a slow steady stream When events arrive under the batch size Then a flush lands within the interval`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(
            toRow = { it },
            driver = driver,
            batchSize = 50,
            flushIntervalMillis = 250,
        )

        var firstFlushAt = 0L
        var firstBatchSize = 0
        val flushed = CompletableDeferred<Unit>()
        controller.onBatchFlushed = { size ->
            if (firstFlushAt == 0L) {
                firstBatchSize = size
                firstFlushAt = nowMillis()
                flushed.complete(Unit)
            }
        }
        controller.start()

        val startedAt = nowMillis()
        repeat(10) { i ->
            controller.submit(event("e$i"))
            delay(100)
        }

        withTimeout(10_000) { flushed.await() }
        val elapsed = firstFlushAt - startedAt
        assertTrue(firstBatchSize < 10, "deadline flush should fire mid-stream, got a batch of $firstBatchSize")
        assertTrue(elapsed < 600, "expected flush within interval, took $elapsed ms")

        controller.close()
    }
}


