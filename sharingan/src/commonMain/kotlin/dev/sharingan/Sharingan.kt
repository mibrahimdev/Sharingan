package dev.sharingan

import kotlinx.coroutines.flow.StateFlow

/**
 * Sharingan — live HTTP · MQTT · Bluetooth logging for Kotlin Multiplatform.
 *
 * This facade is the only entry point most apps need:
 *
 * ```kotlin
 * // HTTP (automatic):
 * HttpClient { install(SharinganKtor) }
 *
 * // MQTT / BLE (one line per client callback):
 * Sharingan.mqtt.publish(topic, payload, qos)
 * Sharingan.ble.notify(device, characteristic, uuid, value)
 *
 * // UI: Android — Sharingan.show(context) or tap the capture notification.
 * //     iOS    — present SharinganViewController() from Swift.
 * ```
 *
 * In release builds depend on the `sharingan-noop` artifact: it mirrors this
 * API with empty implementations, so calls compile identically and ship no UI.
 */
public object Sharingan {

    /** The shared event buffer backing the notification and the log browser. */
    public val store: SharinganStore = SharinganStore()

    /** Manual HTTP logging (automatic when the `SharinganKtor` plugin is installed). */
    public val http: HttpLogger = HttpLogger(store)

    /** MQTT logging hooks. */
    public val mqtt: MqttLogger = MqttLogger(store)

    /** BLE logging hooks. */
    public val ble: BleLogger = BleLogger(store)

    /** All captured events, oldest first. */
    public val events: StateFlow<List<SharinganEvent>> get() = store.events

    /** Whether capture is active (the REC/PAUSED toggle). */
    public val isRecording: StateFlow<Boolean> get() = store.isRecording

    /** Pauses or resumes capture. */
    public fun setRecording(recording: Boolean) {
        store.setRecording(recording)
    }

    /** Clears all captured events. */
    public fun clear() {
        store.clear()
    }
}
