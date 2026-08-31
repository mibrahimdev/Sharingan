package dev.sharingan.internal

import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.Sharingan
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.db.EventRow
import dev.sharingan.db.PersistenceController
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.serialization.json.Json

/**
 * Starts persistence once per process. The only caller is Android's
 * manifest-merged ContentProvider — there is no Kotlin call site to grep.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object Persistence {
    private val started = AtomicBoolean(false)
    private var controller: PersistenceController<SharinganEvent>? = null

    fun start(store: SharinganStore = Sharingan.store) {
        if (!started.compareAndSet(false, true)) return
        controller = PersistenceController(::toRow).also { ctrl ->
            ctrl.start()
            store.onRecord = { event -> ctrl.submit(event) }
            store.onClear = { ctrl.clear() }
        }
    }
}

internal val json = Json { encodeDefaults = true }

// Bodies stay in memory only — the flight-recorder design defaults to
// persistBodies = false. EventDto keeps the fields so a later slice can add
// the opt-in flag on this path.
internal fun toRow(event: SharinganEvent): EventRow = EventRow(
    rawId = event.id,
    timestampMillis = event.timestampMillis,
    type = event.typeName(),
    isFailure = event.isFailure,
    hostOrTopic = event.hostOrTopic(),
    payloadJson = json.encodeToString(EventDto.serializer(), EventDto.fromEvent(event).withoutBodies()),
)

private fun EventDto.withoutBodies(): EventDto = when (this) {
    is HttpDto -> copy(requestBody = null, responseBody = null)
    is MqttDto -> copy(payload = null)
    is BleDto -> copy(payload = null)
}

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
