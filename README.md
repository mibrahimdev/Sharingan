# Sharingan 👁️

**Live HTTP · MQTT · Bluetooth logging for Kotlin Multiplatform — iOS & Android, one API.**

Sharingan is an on-device debug logger. It captures protocol traffic while you use your app, surfaces it in a sticky capture notification (Android) and a built-in log browser (both platforms), and is optimized for **extracting clean, structured logs to hand to a human — or an AI agent** — to pin down bugs across backend, app and firmware.

> The name is a Naruto reference: the eye that sees everything.

- **Three protocol tabs** — HTTP, MQTT, Bluetooth (BLE/GATT), each with live counts, search and quick filters, in a compact terminal-density list.
- **Detail view per event** — timing waterfall, request/response headers (secrets redacted at capture), syntax-colored JSON bodies, QoS/retain flags, GATT operations and decoded values.
- **Share sheet built for agents** — "Copy for AI agent" produces structured Markdown an LLM parses reliably; cURL, raw JSON and the system share sheet are one tap away.
- **Zero release impact** — swap in the `sharingan-noop` artifact: identical API, captures nothing, ships no UI.
- **Minimal** — no image assets, no icon fonts, no bundled typefaces, no DI, no navigation library. The capture core depends only on coroutines (+ Ktor when you use the plugin).

---

## Setup

### Android app (two lines)

```kotlin
dependencies {
    debugImplementation("dev.sharingan:sharingan:0.1.0")
    releaseImplementation("dev.sharingan:sharingan-noop:0.1.0")
}
```

### Kotlin Multiplatform app

Gradle resolves one dependency list for `commonMain`, so pick the artifact with a property:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            if (providers.gradleProperty("release").isPresent) {
                implementation("dev.sharingan:sharingan-noop:0.1.0")
            } else {
                implementation("dev.sharingan:sharingan:0.1.0")
            }
        }
    }
}
```

Build release artifacts with `-Prelease`. (Kotlin/Native dead-code elimination also strips whatever the no-op artifact doesn't reference.)

## Capture

### HTTP — automatic (Ktor)

```kotlin
val client = HttpClient {
    install(SharinganKtor) // that's it
}
```

The plugin records method, URL, status, headers, textual bodies (capped at 64 KB, configurable), duration and a TTFB/Download timing split. `Authorization`, `Cookie`, `Set-Cookie` and `Proxy-Authorization` values are masked **at capture time** — secrets never reach the buffer. Streaming responses (`text/event-stream`, binary) are never consumed.

```kotlin
install(SharinganKtor) {
    captureBodies = true            // default
    maxBodyBytes = 64 * 1024        // default
    redactedHeaders = setOf("Authorization", "X-Api-Key")
}
```

Using OkHttp/NSURLSession directly? Log manually with `Sharingan.http.log(...)`.

### MQTT & BLE — one line per client callback

Sharingan is client-agnostic: wire its loggers into whatever MQTT/BLE library you use.

```kotlin
// MQTT (any client)
Sharingan.mqtt.publish(topic, payloadAsText, qos = 1, retained = false)
Sharingan.mqtt.received(topic, payloadAsText, qos = 1)
Sharingan.mqtt.subscribed("devices/+/commands/#", qos = 1)

// BLE (any GATT client — e.g. Kable)
Sharingan.ble.connect(device = "HR-Monitor-9F")
Sharingan.ble.notify(device, characteristic = "Heart Rate Measurement", uuid = "0x2A37", value = decodedJson)
Sharingan.ble.read(device, characteristic, uuid, value)
Sharingan.ble.error(device, message = "Attribute not found (GATT 0x0A)", characteristic, uuid)
```

<details>
<summary>Kable adapter recipe</summary>

```kotlin
peripheral.observe(characteristic).onEach { bytes ->
    Sharingan.ble.notify(
        device = peripheral.name ?: peripheral.identifier.toString(),
        characteristic = "Heart Rate Measurement",
        uuid = characteristic.characteristicUuid.toString(),
        value = decodeHeartRate(bytes), // your decoder; JSON renders best
    )
}.launchIn(scope)
```
</details>

## Opening the log browser

| Platform | Entry point |
|---|---|
| Android | Tap the **capture notification** (appears on first event), or `Sharingan.show(context)` |
| iOS | Present `SharinganViewController()` from Swift (sheet, push, debug menu, shake) |

```swift
// SwiftUI
.sheet(isPresented: $showLogs) {
    SharinganView() // UIViewControllerRepresentable wrapping SharinganViewController()
}
```

iOS has no sticky-notification equivalent; the view controller is the platform-conventional entry. Everything else — capture API, screens, share sheet — behaves identically on both platforms.

The Android notification shows per-protocol counters, a three-event ticker when expanded, and a Pause/Resume action. It is silent and updated in place. On Android 13+ request `POST_NOTIFICATIONS` (Sharingan declares the permission, your app requests it); without it, capture still works — open the browser with `Sharingan.show(context)`. Note: because the notification is silent, **Do Not Disturb hides it** on most devices — `Sharingan.show(context)` always works.

## Sharing & exporting

Every formatter behind the share sheet is public API, so scripts and agents can produce identical output:

```kotlin
SharinganExport.agentMarkdown(event)      // one event as structured Markdown
SharinganExport.agentMarkdown(events)     // whole session, with counts header
SharinganExport.curl(httpEvent)           // reproducible curl command
SharinganExport.json(event)               // machine-readable single event
SharinganExport.sessionJson(events)       // full session with tool metadata
SharinganExport.summary(events)           // human digest, one line per event
```

## Control surface

```kotlin
Sharingan.events                 // StateFlow<List<SharinganEvent>>, oldest first
Sharingan.isRecording            // StateFlow<Boolean>
Sharingan.setRecording(false)    // pause capture (REC/PAUSED toggle)
Sharingan.clear()                // drop everything
Sharingan.setNotificationEnabled(false)  // Android: opt out of the notification
```

The buffer is an in-memory ring (default 300 events) — nothing is ever written to disk.

## Release builds: what "no effect" means

- `sharingan-noop` mirrors the public API: facade, loggers, `SharinganKtor` (installs, adds no hooks), `Sharingan.show` (no-op), `SharinganViewController()` (empty controller).
- No Compose UI, no resources, no notification, no capture, no disk or network access in the artifact.
- Event model classes stay real, so code that constructs or pattern-matches events behaves identically.
- One caveat: `SharinganScreen()` (the embeddable composable) exists only in the debug artifact — embed it behind your own debug flag, or stick to the platform entry points above.

## Sample app

`sample/composeApp` generates the design's IoT scenario offline (MockEngine): device state calls, a 401 token refresh, a 500 stream timeout, MQTT telemetry with a broker failure, and a BLE heart-rate session.

```bash
./gradlew :sample:composeApp:installDebug          # Android
./gradlew :sample:composeApp:assembleRelease -Psharingan.noop  # parity proof
```

## For AI agents

Integrating Sharingan with an agent or asking one to debug with it? Point it at [AGENTS.md](AGENTS.md) (also mirrored at `llms.txt`) — a terse, complete API reference designed for machine consumption.

## License

Apache-2.0
