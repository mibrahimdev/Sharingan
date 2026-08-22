package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.internal.currentTimeMillis
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
import kotlinx.serialization.json.Json

/**
 * Write-behind persistence: drains events off the store's internal `onRecord`
 * seam into an on-device SQLDelight database so logs survive process death.
 *
 * `record()` keeps its lock-free CAS append and never blocks; [start] hangs a
 * non-blocking `channel.trySend` off the seam, and a single flusher coroutine
 * on `Dispatchers.Default` drains the channel, batching writes by size or time
 * (whichever first) into one `transaction {}` per batch. One session row is
 * created lazily on the first flushed event of the process launch.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PersistenceController(
    private val store: SharinganStore,
    private val driver: SqlDriver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
) {
    private val database = SharinganDatabase(driver)

    // DROP_OLDEST, not DROP_LATEST: a flight recorder must keep the crash-tail,
    // so under backpressure the oldest events are evicted, never the newest.
    private val channel = newEventChannel(CHANNEL_CAPACITY)

    private val started = AtomicBoolean(false)

    private var flusherJob: Job? = null

    // Only the flusher coroutine touches this. Memoized across flushes, but
    // reset if a batch's transaction rolls back so a half-written session row
    // is never referenced by a later batch.
    private var sessionId: String? = null

    /** Test seam: invoked with the batch size after each committed transaction. */
    internal var onBatchFlushed: ((Int) -> Unit)? = null

    /** Wires the store seam and starts the flusher. Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        store.onRecord = { event -> channel.trySend(event) }
        flusherJob = scope.launch { runFlusher() }
    }

    /** Drains and flushes the pending events, then closes the driver. */
    suspend fun stop() {
        if (!started.compareAndSet(true, false)) return
        store.onRecord = null
        channel.close()
        flusherJob?.join()
        scope.cancel()
        driver.close()
    }

    private suspend fun runFlusher() {
        val batch = mutableListOf<SharinganEvent>()
        while (true) {
            val event = try {
                if (batch.isEmpty()) channel.receive()
                else withTimeoutOrNull(flushIntervalMillis) { channel.receive() }
            } catch (e: ClosedReceiveChannelException) {
                break
            }
            if (event == null) {
                flush(batch) // time-based
            } else {
                batch.add(event)
                if (batch.size >= batchSize) flush(batch) // size-based
            }
        }
        flush(batch) // drain the in-flight batch after the channel is closed
    }

    private fun flush(batch: MutableList<SharinganEvent>) {
        if (batch.isEmpty()) return
        val snapshot = batch.toList()
        batch.clear()
        val isNewSession = sessionId == null
        val session = sessionId ?: newSessionId()
        try {
            database.transaction {
                if (isNewSession) insertSession(session)
                snapshot.forEach { persist(it, session) }
            }
            if (isNewSession) sessionId = session
            onBatchFlushed?.invoke(snapshot.size)
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
            started_at = currentTimeMillis(),
            app_id = null,
            build = null,
            os = null,
            device_model = null,
        )
    }

    private fun persist(event: SharinganEvent, session: String) {
        database.sharinganDatabaseQueries.insertEvent(
            id = "$session-${event.id}",
            session_id = session,
            timestamp = event.timestampMillis,
            type = event.typeName(),
            is_failure = if (event.isFailure) 1L else 0L,
            host_or_topic = event.hostOrTopic(),
            payload_json = json.encodeToString(EventDto.serializer(), EventDto.fromEvent(event)),
        )
    }

    internal companion object {
        const val DEFAULT_BATCH_SIZE: Int = 50
        const val DEFAULT_FLUSH_INTERVAL_MILLIS: Long = 250L
        const val CHANNEL_CAPACITY: Int = 1024
    }
}

private val json = Json { encodeDefaults = true }

private fun SharinganEvent.typeName(): String = when (this) {
    is HttpEvent -> "HTTP"
    is MqttEvent -> "MQTT"
    is BleEvent -> "BLE"
}

private fun SharinganEvent.hostOrTopic(): String? = when (this) {
    is HttpEvent -> host
    is MqttEvent -> topic
    is BleEvent -> device
}

// DROP_OLDEST: a flight recorder must keep the crash-tail, so when the channel
// overflows the oldest events are evicted, never the newest.
internal fun newEventChannel(capacity: Int): Channel<SharinganEvent> =
    Channel(capacity = capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

// A session id only needs to be unique within this device's database, but it
// must be collision-free even for two launches in the same millisecond (and two
// test controllers) — a real UUID, not timestamp+Random which collide on
// Kotlin/Native's Default random under rapid successive calls.
@OptIn(ExperimentalUuidApi::class)
private fun newSessionId(): String =
    "session-${Uuid.random()}"
