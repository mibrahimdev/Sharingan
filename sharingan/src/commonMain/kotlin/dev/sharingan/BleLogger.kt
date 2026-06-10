package dev.sharingan

import dev.sharingan.internal.EventIds
import dev.sharingan.internal.currentTimeMillis

/**
 * Records [BleEvent]s into a [SharinganStore].
 *
 * Sharingan is client-agnostic for BLE: call these methods from your GATT
 * callbacks (Kable, RxAndroidBle, CoreBluetooth bridges…). See the project
 * README for adapter recipes.
 */
public class BleLogger(private val store: SharinganStore) {

    /** Records a successful connection to [device]. */
    public fun connect(device: String, error: String? = null) {
        record(BleOperation.CONNECT, device, error = error)
    }

    /** Records a disconnection from [device]. */
    public fun disconnect(device: String, error: String? = null) {
        record(BleOperation.DISCONNECT, device, error = error)
    }

    /** Records service/characteristic discovery on [device]. */
    public fun discover(
        device: String,
        service: String? = null,
        uuid: String? = null,
        detail: String? = null,
    ) {
        record(BleOperation.DISCOVER, device, characteristic = service, uuid = uuid, value = detail)
    }

    /** Records a characteristic read. [value] is the decoded value as text. */
    public fun read(
        device: String,
        characteristic: String? = null,
        uuid: String? = null,
        value: String? = null,
        error: String? = null,
    ) {
        record(BleOperation.READ, device, characteristic, uuid, value, error)
    }

    /** Records a characteristic write. [value] is the written value as text. */
    public fun write(
        device: String,
        characteristic: String? = null,
        uuid: String? = null,
        value: String? = null,
        error: String? = null,
    ) {
        record(BleOperation.WRITE, device, characteristic, uuid, value, error)
    }

    /** Records a characteristic notification/indication. */
    public fun notify(
        device: String,
        characteristic: String? = null,
        uuid: String? = null,
        value: String? = null,
    ) {
        record(BleOperation.NOTIFY, device, characteristic, uuid, value)
    }

    /** Records a failed GATT operation. */
    public fun error(
        device: String,
        message: String,
        characteristic: String? = null,
        uuid: String? = null,
    ) {
        record(BleOperation.ERROR, device, characteristic, uuid, error = message)
    }

    private fun record(
        operation: BleOperation,
        device: String,
        characteristic: String? = null,
        uuid: String? = null,
        value: String? = null,
        error: String? = null,
    ) {
        store.record(
            BleEvent(
                id = EventIds.next("ble-"),
                timestampMillis = currentTimeMillis(),
                operation = operation,
                device = device,
                characteristic = characteristic,
                uuid = uuid,
                payload = value,
                sizeBytes = value?.encodeToByteArray()?.size?.toLong(),
                error = error,
            )
        )
    }
}
