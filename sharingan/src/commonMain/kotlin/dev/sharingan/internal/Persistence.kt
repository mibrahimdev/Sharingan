package dev.sharingan.internal

import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.Sharingan
import dev.sharingan.SharinganEvent
import dev.sharingan.SharinganStore
import dev.sharingan.db.EventRow
import dev.sharingan.db.PersistenceController
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Starts persistence once per process. The only caller is Android's
 * manifest-merged ContentProvider — there is no Kotlin call site to grep.
 * [stop] tears the wiring down so a new lifecycle can start again.
 *
 * ponytail: not unit-tested. [start] builds a real driver, and on Android
 * DriverFactory.create() needs the ContentProvider-installed Context, which a
 * JVM unit test has none of. Covering this would mean either Robolectric or a
 * controller-injection seam; neither is worth it for straight-line wiring.
 * The persistence behaviour it wires is tested in :sharingan-db.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object Persistence {
    private val started = AtomicBoolean(false)
    private var controller: PersistenceController<SharinganEvent>? = null
    private var store: SharinganStore? = null

    fun start(store: SharinganStore = Sharingan.store) {
        if (!started.compareAndSet(false, true)) return
        val controller = PersistenceController(::toRow)
        // Seams before flusher: start() is lazy so the old order worked by luck; this encodes the dependency.
        store.onRecord = { event -> controller.submit(event) }
        store.onClear = { controller.clear() }
        this.store = store
        this.controller = controller
        controller.start()
    }

    /** Unwires the seams and closes the controller; [start] may run again afterwards. */
    suspend fun stop() {
        val controller = controller ?: return
        store?.onRecord = null
        store?.onClear = null
        store = null
        this.controller = null
        controller.close()
        started.store(false)
    }
}

internal val json = Json { encodeDefaults = true }

// Bodies stay off disk (design default persistBodies = false); EventDto keeps the fields for the slice-5 opt-in.
internal fun toRow(event: SharinganEvent): EventRow =
    EventRow(
        rawId = event.id,
        timestampMillis = event.timestampMillis,
        type = event.typeName(),
        isFailure = event.isFailure,
        hostOrTopic = event.hostOrTopic(),
        payloadJson = json.encodeToString(EventDto.serializer(), EventDto.fromEvent(event).withoutBodies()),
    )

private fun EventDto.withoutBodies(): EventDto =
    when (this) {
        is HttpDto -> copy(requestBody = null, responseBody = null)
        is MqttDto -> copy(payload = null)
        is BleDto -> copy(payload = null)
    }

private fun SharinganEvent.typeName(): String =
    when (this) {
        is HttpEvent -> "HTTP"
        is MqttEvent -> "MQTT"
        is BleEvent -> "BLE"
    }

private fun SharinganEvent.hostOrTopic(): String? =
    when (this) {
        is HttpEvent -> host
        is MqttEvent -> topic
        is BleEvent -> device
    }
