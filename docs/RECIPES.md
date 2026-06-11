# Integration recipes

## MQTT — clients with callbacks (KMQTT, HiveMQ, Paho…)

```kotlin
client.subscribe(filter, qos) { /* granted */ Sharingan.mqtt.subscribed(filter, qos) }
client.onMessage { msg -> Sharingan.mqtt.received(msg.topic, msg.payload.decodeToString(), msg.qos) }
client.publish(topic, bytes, qos, retained).also {
    Sharingan.mqtt.publish(topic, bytes.decodeToString(), qos, retained)
}
```

Tip: log payloads as JSON text whenever possible — Sharingan pretty-prints and
syntax-colors valid JSON in the detail view and in agent exports.

## BLE — Kable

```kotlin
val peripheral = scope.peripheral(advertisement)

peripheral.state.onEach { state ->
    when (state) {
        is State.Connected -> Sharingan.ble.connect(peripheral.nameOrId())
        is State.Disconnected -> Sharingan.ble.disconnect(peripheral.nameOrId(), state.status?.toString())
        else -> Unit
    }
}.launchIn(scope)

peripheral.observe(hrCharacteristic).onEach { bytes ->
    Sharingan.ble.notify(
        device = peripheral.nameOrId(),
        characteristic = "Heart Rate Measurement",
        uuid = hrCharacteristic.characteristicUuid.toString(),
        value = """{"bpm":${bytes.toHeartRate()}}""",
    )
}.launchIn(scope)

private fun Peripheral.nameOrId() = name ?: identifier.toString()
```

## iOS — SwiftUI wrapper

```swift
import SwiftUI
import Sharingan // your shared framework

struct SharinganView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SharinganViewControllerKt.SharinganViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

// usage
.sheet(isPresented: $showLogs) { SharinganView() }
```

## iOS — Live Activity analog (optional, app-side)

iOS does not allow a library to ship a sticky notification. If you want the
design's lock-screen capture card, add a widget-extension Live Activity in
your app and drive it from Sharingan's counters:

```swift
// In your app target (requires an ActivityKit widget extension):
// observe Sharingan.shared.events via SKIE / KMP-NativeCoroutines and update
// Activity attributes with: http/mqtt/ble counts + last event line.
```

This stays app-side by design — widget extensions can't ship from a Kotlin library.

## Shake-to-open (Android)

```kotlin
// Any shake detector; on trigger:
Sharingan.show(context)
```
