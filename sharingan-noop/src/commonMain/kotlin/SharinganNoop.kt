// sharingan-noop — API mirror of dev.sharingan:sharingan with empty
// implementations. Depend on this artifact in release builds: every call
// compiles identically, nothing is captured, no UI ships.
package dev.sharingan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No-op store: accepts nothing, [events] is always empty. */
public class SharinganStore(
    public val capacity: Int = DEFAULT_CAPACITY,
) {
    private val _events = MutableStateFlow<List<SharinganEvent>>(emptyList())
    private val _isRecording = MutableStateFlow(false)

    public val events: StateFlow<List<SharinganEvent>> = _events.asStateFlow()
    public val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    public fun record(event: SharinganEvent) {}
    public fun setRecording(recording: Boolean) {}
    public fun clear() {}

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 300
    }
}

/** No-op HTTP logger. */
public class HttpLogger(
    @Suppress("UNUSED_PARAMETER") store: SharinganStore,
    @Suppress("UNUSED_PARAMETER") redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
) {
    public fun log(
        method: String,
        url: String,
        statusCode: Int? = null,
        durationMillis: Long? = null,
        requestHeaders: List<Pair<String, String>> = emptyList(),
        responseHeaders: List<Pair<String, String>> = emptyList(),
        requestBody: String? = null,
        responseBody: String? = null,
        contentType: String? = null,
        responseSizeBytes: Long? = null,
        timing: List<TimingPhase> = emptyList(),
        error: String? = null,
    ) {}

    public companion object {
        public const val REDACTED_VALUE: String = "••••"
        public val DEFAULT_REDACTED_HEADERS: Set<String> =
            setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")
    }
}

/** No-op MQTT logger. */
public class MqttLogger(@Suppress("UNUSED_PARAMETER") store: SharinganStore) {
    public fun publish(
        topic: String,
        payload: String?,
        qos: Int = 0,
        retained: Boolean = false,
        error: String? = null,
    ) {}

    public fun received(topic: String, payload: String?, qos: Int = 0, retained: Boolean = false) {}
    public fun subscribed(topicFilter: String, qos: Int = 0) {}
}

/** No-op BLE logger. */
public class BleLogger(@Suppress("UNUSED_PARAMETER") store: SharinganStore) {
    public fun connect(device: String, error: String? = null) {}
    public fun disconnect(device: String, error: String? = null) {}
    public fun discover(device: String, service: String? = null, uuid: String? = null, detail: String? = null) {}
    public fun read(device: String, characteristic: String? = null, uuid: String? = null, value: String? = null, error: String? = null) {}
    public fun write(device: String, characteristic: String? = null, uuid: String? = null, value: String? = null, error: String? = null) {}
    public fun notify(device: String, characteristic: String? = null, uuid: String? = null, value: String? = null) {}
    public fun error(device: String, message: String, characteristic: String? = null, uuid: String? = null) {}
}

/** No-op facade: same surface as the real artifact, captures nothing. */
public object Sharingan {
    public val store: SharinganStore = SharinganStore()
    public val http: HttpLogger = HttpLogger(store)
    public val mqtt: MqttLogger = MqttLogger(store)
    public val ble: BleLogger = BleLogger(store)

    public val events: StateFlow<List<SharinganEvent>> get() = store.events
    public val isRecording: StateFlow<Boolean> get() = store.isRecording

    public fun setRecording(recording: Boolean) {}
    public fun clear() {}
}

/** No-op exporters: signatures match, output is empty. */
public object SharinganExport {
    public fun agentMarkdown(event: SharinganEvent): String = ""
    public fun agentMarkdown(events: List<SharinganEvent>): String = ""
    public fun curl(event: HttpEvent): String = ""
    public fun json(event: SharinganEvent): String = ""
    public fun sessionJson(events: List<SharinganEvent>): String = ""
    public fun summary(events: List<SharinganEvent>): String = ""
}
