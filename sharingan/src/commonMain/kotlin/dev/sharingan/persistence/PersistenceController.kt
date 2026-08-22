package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.internal.currentTimeMillis
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
internal class PersistenceController(
    private val store: SharinganStore,
    private val driver: SqlDriver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
) {
    private val database = SharinganDatabase(driver)
    private val channel = Channel<SharinganEvent>(capacity = CHANNEL_CAPACITY)

    private var sessionId: String? = null

    /** Test seam: invoked with the batch size after each committed transaction. */
    internal var onBatchFlushed: ((Int) -> Unit)? = null

    /** Wires the store seam and starts the flusher. Idempotent per controller. */
    fun start() {
        store.onRecord = { event -> channel.trySend(event) }
        scope.launch { runFlusher() }
    }

    /** Unwires the store seam and stops the flusher. */
    fun stop() {
        store.onRecord = null
        scope.cancel()
    }

    private suspend fun runFlusher() {
        val batch = mutableListOf<SharinganEvent>()
        while (true) {
            val event = if (batch.isEmpty()) {
                channel.receive()
            } else {
                withTimeoutOrNull(flushIntervalMillis) { channel.receive() }
            }
            if (event == null) {
                flush(batch) // time-based
            } else {
                batch.add(event)
                if (batch.size >= batchSize) flush(batch) // size-based
            }
        }
    }

    private fun flush(batch: MutableList<SharinganEvent>) {
        if (batch.isEmpty()) return
        val snapshot = batch.toList()
        batch.clear()
        database.transaction {
            snapshot.forEach { persist(it) }
        }
        onBatchFlushed?.invoke(snapshot.size)
    }

    private fun persist(event: SharinganEvent) {
        val session = ensureSession()
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

    private fun ensureSession(): String {
        sessionId?.let { return it }
        val id = newSessionId()
        database.sharinganDatabaseQueries.insertSession(
            id = id,
            started_at = currentTimeMillis(),
            app_id = null,
            build = null,
            os = null,
            device_model = null,
        )
        sessionId = id
        return id
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

// A session id only needs to be unique within this device's database. A
// timestamp plus a random component makes two process launches that begin in
// the same millisecond (and two test controllers) effectively collision-free.
private fun newSessionId(): String =
    "session-${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}"
