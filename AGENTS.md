# Sharingan — agent reference

On-device debug logger for Kotlin Multiplatform (Android + iOS). Captures HTTP, MQTT and BLE traffic into an in-memory ring buffer (default 300 events, never persisted) and renders a log browser. This file is the complete public API; everything lives in the `dev.sharingan` package unless noted.

## Artifacts

| Coordinate | Use |
|---|---|
| `io.github.mibrahimdev:sharingan:0.1.1` | debug builds (capture + UI) |
| `io.github.mibrahimdev:sharingan-noop:0.1.1` | release builds (same API, inert, no UI) |

Android: `debugImplementation` / `releaseImplementation` pair.
KMP commonMain: choose one via a Gradle property (single dependency list).
Never reference `dev.sharingan.ui.SharinganScreen` in code shared with release builds — it exists only in the debug artifact. All other API exists in both.

## Capture API

```kotlin
// HTTP — automatic via Ktor plugin (package dev.sharingan.ktor)
HttpClient { install(SharinganKtor) }
HttpClient { install(SharinganKtor) {
    store = Sharingan.store          // SharinganStore, default shown
    redactedHeaders = setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie") // default
    captureBodies = true             // textual content types only; SSE/binary never read
    maxBodyBytes = 65536             // longer bodies truncated with a marker
} }

// HTTP — manual (all params optional except method, url)
Sharingan.http.log(method, url, statusCode, durationMillis, requestHeaders /*List<Pair<String,String>>*/,
    responseHeaders, requestBody, responseBody, contentType, responseSizeBytes,
    timing /*List<TimingPhase(label, millis)>*/, error)

// MQTT
Sharingan.mqtt.publish(topic, payload /*String?*/, qos = 0, retained = false, error = null)
Sharingan.mqtt.received(topic, payload, qos = 0, retained = false)
Sharingan.mqtt.subscribed(topicFilter, qos = 0)

// BLE
Sharingan.ble.connect(device, error = null)
Sharingan.ble.disconnect(device, error = null)
Sharingan.ble.discover(device, service = null, uuid = null, detail = null)
Sharingan.ble.read(device, characteristic = null, uuid = null, value = null, error = null)
Sharingan.ble.write(device, characteristic = null, uuid = null, value = null, error = null)
Sharingan.ble.notify(device, characteristic = null, uuid = null, value = null)
Sharingan.ble.error(device, message, characteristic = null, uuid = null)
```

Payload/value strings that parse as JSON get pretty-printing and syntax colors in the UI; anything else renders verbatim. Header values for redacted names are replaced with `••••` at capture time.

## Observe & control

```kotlin
Sharingan.events: StateFlow<List<SharinganEvent>>   // oldest first
Sharingan.isRecording: StateFlow<Boolean>
Sharingan.setRecording(Boolean); Sharingan.clear()
Sharingan.store: SharinganStore                     // SharinganStore(capacity = 300) constructible for isolation
```

## Event model (sealed interface SharinganEvent: id, timestampMillis, error, isFailure)

```kotlin
HttpEvent(method, url, statusCode?, durationMillis?, requestHeaders, responseHeaders,
          requestBody?, responseBody?, contentType?, responseSizeBytes?, timing, error?)
  // derived: host, path (with query); isFailure = error != null || statusCode >= 400
MqttEvent(direction /*PUBLISH|RECEIVE|SUBSCRIBE*/, topic, qos, retained, payload?, payloadSizeBytes?, error?)
BleEvent(operation /*CONNECT|DISCONNECT|DISCOVER|READ|WRITE|NOTIFY|ERROR*/, device,
         characteristic?, uuid?, payload?, sizeBytes?, error?)
```

## Export (object SharinganExport — same formatters as the in-app share sheet)

```kotlin
agentMarkdown(event): String        // "## GET /path" + status/host lines + headers + ```json body
agentMarkdown(events): String       // "# Sharingan session export" + counts + per-event sections
curl(httpEvent): String             // reproducible curl; redacted headers stay masked
json(event): String                 // {"protocol":"http"|"mqtt"|"ble", ...}
sessionJson(events): String         // {"tool":"sharingan","eventCount":N,"events":[...]}
summary(events): String             // one line per event, clock-stamped
```

To debug with a user: ask them to open Sharingan → (optionally open the failing event) → Share → **Copy for AI agent** → paste. The Markdown contains method/path/status/host, request headers, and the response body fenced as `json`.

## UI entry points

```kotlin
// Android (androidMain): notification appears on first captured event (silent, ongoing,
// Pause/Resume action, tap opens SharinganActivity). Requires POST_NOTIFICATIONS grant on 33+.
Sharingan.show(context)                  // open browser directly
Sharingan.setNotificationEnabled(false)  // opt out

// iOS (iosMain): present from Swift/Kotlin
SharinganViewController(): UIViewController
presentSharingan(animated: Boolean = true)  // presents over topmost VC, any thread

// iOS Swift facade spellings (Kotlin top-level functions map to SharinganViewControllerKt):
// SharinganViewControllerKt.SharinganViewController()  → UIViewController
// SharinganViewControllerKt.presentSharingan(animated: true)

// Compose (debug artifact only, package dev.sharingan.ui)
@Composable SharinganScreen(modifier, darkTheme = isSystemInDarkTheme(), store = Sharingan.store)
```

## Facts agents commonly need

- Zero-setup init on Android via manifest-merged ContentProvider; no Application code.
- Buffer is memory-only; process death clears it. Capacity via `SharinganStore(capacity)`.
- The Ktor plugin rethrows transport failures untouched after recording them (statusCode null, error set).
- Thread-safe: store updates are atomic CAS; loggers callable from any thread.
- Min targets: Android API 24, iOS arm64 + simulator arm64. Requires Ktor 3.x for the plugin.
- KMP/iOS: dependency must be `api(...)` in `iosMain` (not just `commonMain`) and the framework block must `export(...)` it — without both, Kotlin/Native emits an empty header.
- XCFramework build tasks: `./gradlew :sharingan:assembleSharinganReleaseXCFramework` (debug tool) and `./gradlew :sharingan-noop:assembleSharinganReleaseXCFramework` (inert twin); outputs at `<module>/build/XCFrameworks/release/Sharingan.xcframework`.

## Public API stability (BCV)

The public API of both `:sharingan` and `:sharingan-noop` is guarded by Kotlin's
binary-compatibility-validator. The golden dumps are committed at
`<module>/api/<module>.api` (Android/JVM) and `<module>/api/<module>.klib.api`
(iOS ABI) and cover the full surface, including the iOS-only entry points.

- `./gradlew apiCheck` — fails if the public API drifts from the committed dumps. Runs on every PR (`.github/workflows/api-check.yml`, macOS so the iOS klib targets build).
- `./gradlew apiDump` — regenerate the dumps after an *intentional* API change, then commit the updated `api/*.api` files in the same PR.
