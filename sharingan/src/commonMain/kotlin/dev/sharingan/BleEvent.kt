package dev.sharingan

/** The kind of Bluetooth Low Energy operation an event describes. */
public enum class BleOperation {
    CONNECT,
    DISCONNECT,
    DISCOVER,
    READ,
    WRITE,
    NOTIFY,
    ERROR,
}

/**
 * A captured BLE (GATT) operation.
 *
 * [payload] carries the decoded value as text — JSON is rendered with syntax
 * colors in the detail screen, anything else is shown verbatim.
 */
public data class BleEvent(
    override val id: String,
    override val timestampMillis: Long,
    public val operation: BleOperation,
    public val device: String,
    public val characteristic: String? = null,
    public val uuid: String? = null,
    public val payload: String? = null,
    public val sizeBytes: Long? = null,
    override val error: String? = null,
) : SharinganEvent {

    /** Failures are explicit errors or [BleOperation.ERROR] operations. */
    override val isFailure: Boolean get() = error != null || operation == BleOperation.ERROR
}
