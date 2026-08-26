package dev.sharingan.db

import app.cash.sqldelight.db.SqlDriver
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * Write-behind persistence: drains events off the caller's seam into an
 * on-device SQLDelight database so logs survive process death.
 *
 * [submit] is a non-blocking [Channel.trySend]; a flusher coroutine batches
 * writes by size or time into one transaction per batch, running [toRow] off
 * the hot path.
 *
 * Uses `select`/`onTimeout` ([ExperimentalCoroutinesApi]) — re-validate against
 * the controller tests on a coroutines bump.
 */
@OptIn(ExperimentalAtomicApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
public class PersistenceController<T : Any> private constructor(
    private val toRow: (T) -> EventRow,
    private val driver: SqlDriver,
    private val ownsDriver: Boolean,
    private val scope: CoroutineScope,
    private val batchSize: Int,
    private val flushIntervalMillis: Long,
    channelCapacity: Int,
) {
    public constructor(
        toRow: (T) -> EventRow,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
    ) : this(
        toRow = toRow,
        driver = DriverFactory().create(),
        ownsDriver = true,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        batchSize = batchSize,
        flushIntervalMillis = flushIntervalMillis,
        channelCapacity = CHANNEL_CAPACITY,
    )

    internal constructor(
        toRow: (T) -> EventRow,
        driver: SqlDriver,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        batchSize: Int = DEFAULT_BATCH_SIZE,
        flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
        channelCapacity: Int = CHANNEL_CAPACITY,
    ) : this(
        toRow = toRow,
        driver = driver,
        ownsDriver = false,
        scope = scope,
        batchSize = batchSize,
        flushIntervalMillis = flushIntervalMillis,
        channelCapacity = channelCapacity,
    )

    private val database = SharinganDatabase(driver)
    private val channel = newEventChannel<T>(channelCapacity)

    // AtomicReference so a racing stop() can't read a stale null and skip the join.
    private val flusherJob = AtomicReference<Job?>(null)

    // Terminal flag; the controller is single-use once stopped.
    private val stopped = AtomicBoolean(false)

    // Flusher-thread only; reset on rollback so a rolled-back session row isn't reused.
    private var sessionId: String? = null

    /** Test seam: batch size after each committed transaction. */
    internal var onBatchFlushed: ((Int) -> Unit)? = null

    private val flusherStarts = AtomicInt(0)

    /** Test seam: flusher coroutines that actually started. */
    internal fun flusherStartCount(): Int = flusherStarts.load()

    /**
     * Wires the seam and starts the flusher. Idempotent before [stop]; after
     * [stop] the controller is single-use and [start] throws.
     */
    public fun start() {
        if (stopped.load()) error("PersistenceController is single-use; already stopped")
        // LAZY + start only on the CAS win: an eager launch would need a cancel()
        // on the losing branch, which could prompt-cancel a receive and drop an event.
        val job = scope.launch(start = CoroutineStart.LAZY) { runFlusher() }
        if (flusherJob.compareAndSet(null, job)) job.start()
    }

    /** Submits [event] to the flusher. Never blocks the caller. */
    public fun submit(event: T) {
        channel.trySend(event)
    }

    /**
     * Drains pending events, then stops the flusher. The driver stays open.
     *
     * WARNING: does NOT detach the caller's `store.onRecord` seam — events
     * submitted after [stop] hit a closed channel and are dropped. Detaching the
     * seam is the caller's responsibility.
     */
    public suspend fun stop() {
        stopped.store(true)
        // Close first: a start() that won the CAS after stopped=true then lands
        // on a closed channel and exits at once.
        channel.close()
        val job = flusherJob.exchange(null) ?: return
        job.join()
    }

    /** Closes the driver only if this controller opened it. Like [stop], does not detach the seam. */
    public suspend fun close() {
        stop()
        scope.cancel()
        if (ownsDriver) driver.close()
    }

    private suspend fun runFlusher() {
        flusherStarts.incrementAndFetch()
        val batch = mutableListOf<T>()
        var deadline = 0L
        while (true) {
            val event = if (batch.isEmpty()) {
                try {
                    channel.receive()
                } catch (e: ClosedReceiveChannelException) {
                    break
                }
            } else {
                // Atomic receive-or-timeout; withTimeoutOrNull could prompt-cancel
                // an already-taken element and lose it.
                var closed = false
                select<T?> {
                    channel.onReceiveCatching { result ->
                        if (result.isClosed) {
                            closed = true
                            null
                        } else {
                            result.getOrNull()
                        }
                    }
                    onTimeout(deadline - nowMillis()) { null }
                }.also { if (closed) break }
            }
            if (event == null) {
                flush(batch)
            } else {
                if (batch.isEmpty()) deadline = nowMillis() + flushIntervalMillis
                batch.add(event)
                if (batch.size >= batchSize) flush(batch)
            }
        }
        flush(batch) // drain the in-flight batch after the channel is closed
    }

    private fun flush(batch: MutableList<T>) {
        if (batch.isEmpty()) return
        val snapshot = batch.toList()
        batch.clear()
        val isNewSession = sessionId == null
        val session = sessionId ?: newSessionId()
        try {
            // Map inside the try so a throwing mapper drops the batch, not the flusher.
            val rows = snapshot.map(toRow)
            database.transaction {
                if (isNewSession) insertSession(session)
                rows.forEach { persist(it, session) }
            }
            if (isNewSession) sessionId = session
            onBatchFlushed?.invoke(rows.size)
        } catch (t: Throwable) {
            // Reset the session in case its row rolled back with this transaction.
            if (isNewSession) sessionId = null
            println("Sharingan persistence: dropped a batch of ${snapshot.size} events ($t)")
        }
    }

    private fun insertSession(id: String) {
        database.sharinganDatabaseQueries.insertSession(
            id = id,
            started_at = nowMillis(),
            app_id = null,
            build = null,
            os = null,
            device_model = null,
        )
    }

    private fun persist(row: EventRow, session: String) {
        database.sharinganDatabaseQueries.insertEvent(
            id = "$session-${row.rawId}",
            session_id = session,
            timestamp = row.timestampMillis,
            type = row.type,
            is_failure = if (row.isFailure) 1L else 0L,
            host_or_topic = row.hostOrTopic,
            payload_json = row.payloadJson,
        )
    }

    internal companion object {
        internal const val DEFAULT_BATCH_SIZE: Int = 50
        internal const val DEFAULT_FLUSH_INTERVAL_MILLIS: Long = 250L
        internal const val CHANNEL_CAPACITY: Int = 1024
    }
}

// DROP_OLDEST keeps the crash-tail — the newest events matter most here.
internal fun <T> newEventChannel(capacity: Int): Channel<T> =
    Channel(capacity = capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

// UUID, not timestamp+Random: the latter collides on Kotlin/Native under rapid calls.
@OptIn(ExperimentalUuidApi::class)
private fun newSessionId(): String = "session-${Uuid.random()}"
