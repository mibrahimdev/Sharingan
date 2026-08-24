package dev.sharingan.db

import app.cash.sqldelight.db.SqlDriver
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Write-behind persistence: drains events off the caller's seam into an
 * on-device SQLDelight database so logs survive process death.
 *
 * [submit] is a single non-blocking [Channel.trySend]; the flusher coroutine on
 * [Dispatchers.Default] batches writes by size or time (whichever first) into
 * one transaction per batch. The caller-supplied [toRow] mapping runs on the
 * flusher, never on the hot path.
 */
@OptIn(ExperimentalAtomicApi::class)
public class PersistenceController<T : Any> private constructor(
    private val toRow: (T) -> EventRow,
    private val driver: SqlDriver,
    private val ownsDriver: Boolean,
    private val scope: CoroutineScope,
    private val batchSize: Int,
    private val flushIntervalMillis: Long,
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
    )

    internal constructor(
        toRow: (T) -> EventRow,
        driver: SqlDriver,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        batchSize: Int = DEFAULT_BATCH_SIZE,
        flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
    ) : this(
        toRow = toRow,
        driver = driver,
        ownsDriver = false,
        scope = scope,
        batchSize = batchSize,
        flushIntervalMillis = flushIntervalMillis,
    )

    private val database = SharinganDatabase(driver)

    // DROP_OLDEST, not DROP_LATEST: a flight recorder must keep the crash-tail,
    // so under backpressure the oldest events are evicted, never the newest.
    private val channel = newEventChannel<T>(CHANNEL_CAPACITY)

    private val started = AtomicBoolean(false)

    private var flusherJob: Job? = null

    // Only the flusher coroutine touches this. Memoized across flushes, but
    // reset if a batch's transaction rolls back so a half-written session row
    // is never referenced by a later batch.
    private var sessionId: String? = null

    /** Test seam: invoked with the batch size after each committed transaction. */
    internal var onBatchFlushed: ((Int) -> Unit)? = null

    /** Wires the seam and starts the flusher. Idempotent. */
    public fun start() {
        if (!started.compareAndSet(false, true)) return
        flusherJob = scope.launch { runFlusher() }
    }

    /** Submits [event] to the flusher. Non-blocking and allocation-free. */
    public fun submit(event: T) {
        channel.trySend(event)
    }

    /**
     * Drains and flushes the pending events, then stops the flusher.
     * The driver stays open so other readers can keep using the database.
     *
     * WARNING: this does NOT detach the caller's `store.onRecord` seam. Events
     * submitted after `stop()` are trySend'd to a closed channel and silently
     * dropped. Full teardown (detaching the seam) is the caller's responsibility.
     */
    public suspend fun stop() {
        if (!started.compareAndSet(true, false)) return
        channel.close()
        flusherJob?.join()
    }

    /**
     * Stops the flusher, cancels the scope, and closes the driver.
     *
     * NOTE: this also does not detach the caller's seam; do that before calling
     * `close()` if you need to stop event submission.
     */
    public suspend fun close() {
        stop()
        scope.cancel()
        if (ownsDriver) driver.close()
    }

    private suspend fun runFlusher() {
        val batch = mutableListOf<T>()
        var deadline = 0L
        while (true) {
            val event = try {
                if (batch.isEmpty()) channel.receive()
                // Non-positive timeout returns null immediately; the deadline has
                // already passed and we should flush right away.
                else withTimeoutOrNull(deadline - nowMillis()) { channel.receive() }
            } catch (e: ClosedReceiveChannelException) {
                break
            }
            if (event == null) {
                flush(batch) // deadline reached
            } else {
                if (batch.isEmpty()) deadline = nowMillis() + flushIntervalMillis
                batch.add(event)
                if (batch.size >= batchSize) flush(batch) // size-based
            }
        }
        flush(batch) // drain the in-flight batch after the channel is closed
    }

    private fun flush(batch: MutableList<T>) {
        if (batch.isEmpty()) return
        val snapshot = batch.toList()
        batch.clear()
        val rows = snapshot.map(toRow)
        val isNewSession = sessionId == null
        val session = sessionId ?: newSessionId()
        try {
            database.transaction {
                if (isNewSession) insertSession(session)
                rows.forEach { persist(it, session) }
            }
            if (isNewSession) sessionId = session
            onBatchFlushed?.invoke(rows.size)
        } catch (t: Throwable) {
            // One failing batch must not kill the flusher. Drop it and keep
            // draining; re-derive the session on the next batch in case its row
            // was rolled back with this transaction.
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

// DROP_OLDEST: a flight recorder must keep the crash-tail, so when the channel
// overflows the oldest events are evicted, never the newest.
internal fun <T> newEventChannel(capacity: Int): Channel<T> =
    Channel(capacity = capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

// A session id only needs to be unique within this device's database, but it
// must be collision-free even for two launches in the same millisecond (and two
// test controllers) — a real UUID, not timestamp+Random which collide on
// Kotlin/Native's Default random under rapid successive calls.
@OptIn(ExperimentalUuidApi::class)
private fun newSessionId(): String = "session-${Uuid.random()}"
