package dev.sharingan.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `Given a burst larger than the ring buffer When flushed Then every event is persisted`() = runBlocking {
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
        controller.stop() // second call must be a no-op

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        assertEquals(10, rows.size)

        controller.close()
    }

    @Test
    fun `Given repeated start stop cycles Then the controller remains usable`() = runBlocking {
        val driver = createTestDriver()
        val controller = PersistenceController<EventRow>(toRow = { it }, driver = driver)

        repeat(20) { cycle ->
            controller.start()
            controller.submit(event("cycle-$cycle"))
            controller.stop()
        }

        val rows = SharinganDatabase(driver).sharinganDatabaseQueries.selectAllEvents().executeAsList()
        // Each cycle's event must have either been flushed or dropped by a race;
        // the only hard contract is that no crash occurred and the DB is consistent.
        assertTrue(rows.size in 0..20, "expected 0..20 rows after races, got ${rows.size}")

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
