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
import shared // your shared framework

// No-wrapper alternative — presents over the topmost view controller, any thread:
SharinganViewControllerKt.presentSharingan(animated: true)

// Or embed the view controller yourself:
struct SharinganView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SharinganViewControllerKt.SharinganViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

// usage
.sheet(isPresented: $showLogs) { SharinganView() }
```

## iOS — manual HTTP logging from Swift (URLSession, no Ktor)

A pure-Swift app (or any non-Ktor stack) feeds the viewer by calling
`Sharingan.http.log(...)` itself. One Swift gotcha: Kotlin default arguments
do **not** bridge to Swift, so every parameter is required — pass `[]` for the
arrays you don't have and `nil` for optional values. The numeric params are
boxed Kotlin types (`SharinganInt?`, `SharinganLong?`) that accept integer
literals; headers are `SharinganKotlinPair<NSString, NSString>`.

```swift
import shared

Sharingan.shared.http.log(
    method: "GET",
    url: "https://api.example.com/users",
    statusCode: 200,                 // SharinganInt?  — literal is fine
    durationMillis: 142,             // SharinganLong? — literal is fine
    requestHeaders: [],              // [SharinganKotlinPair<NSString, NSString>]
    responseHeaders: [],
    requestBody: nil,
    responseBody: nil,
    contentType: "application/json",
    responseSizeBytes: 5645,
    timing: [],                      // [SharinganTimingPhase] — not optional
    error: nil
)
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
