package dev.sharingan

/**
 * A single captured event, shown as one row in the Sharingan log browser.
 *
 * Three protocol-specific implementations exist: [HttpEvent], [MqttEvent] and
 * [BleEvent]. All events share an [id], a capture [timestampMillis] (epoch
 * milliseconds) and an optional [error] describing why the event failed.
 */
public sealed interface SharinganEvent {
    /** Unique, monotonically increasing id assigned at capture time. */
    public val id: String

    /** Capture wall-clock time in epoch milliseconds. */
    public val timestampMillis: Long

    /** Human-readable failure reason, or `null` if the event succeeded. */
    public val error: String?

    /** Whether this event represents a failure (drives the red rail in the UI). */
    public val isFailure: Boolean get() = error != null
}
