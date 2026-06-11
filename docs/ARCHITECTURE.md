# Sharingan — Architecture & Handoff Notes

Technical handoff for maintainers. Every claim below is checkable against a named file.
Companion docs: [README.md](../README.md) (user-facing), [AGENTS.md](../AGENTS.md) (terse API reference, mirrored at `llms.txt`), [docs/RECIPES.md](RECIPES.md) (integration snippets). The git log is itself a design doc — each commit message states the deliverable and rationale for that step (`git log`).

---

## 1. System overview

Sharingan is an on-device debug logger for Kotlin Multiplatform (Android API 24+, iOS arm64 + simulator arm64). It captures HTTP, MQTT and BLE traffic into an in-memory ring buffer, renders a Compose Multiplatform log browser, and exports events as agent-friendly Markdown / cURL / JSON / digest text. Nothing is ever persisted.

### Module map

```text
:sharingan            debug artifact — capture core + Ktor plugin + Compose UI
                      + Android notification/activity + iOS view controller
:sharingan-noop       release artifact — same public API, inert bodies, no UI payload
:sample:composeApp    demo app — deterministic IoT traffic via Ktor MockEngine;
                      can compile against either artifact (-Psharingan.noop)
```

Modules are declared in [settings.gradle.kts](../settings.gradle.kts). `:sharingan` and `:sharingan-noop` publish as `dev.sharingan:sharingan` / `dev.sharingan:sharingan-noop` (version in [gradle/libs.versions.toml](../gradle/libs.versions.toml), key `sharingan`).

### Data flow

```text
 capture entry points                store                      consumers
┌──────────────────────┐
│ SharinganKtor plugin │──┐
│ (ktor/SharinganKtor) │  │   ┌─────────────────────┐   ┌──────────────────────────┐
├──────────────────────┤  ├──▶│ SharinganStore       │──▶│ UI: SharinganScreen      │
│ Sharingan.http .log  │  │   │ ring buffer (300)    │   │  wrapper → stateless     │
│ Sharingan.mqtt .pub… │──┘   │ StateFlow<List<…>>   │   │  *Content composables    │
│ Sharingan.ble  .noti…│      │ + isRecording flag   │   ├──────────────────────────┤
└──────────────────────┘      └─────────────────────┘   │ CaptureNotification      │
  header redaction happens          ▲      │             │ (Android, observes flow) │
  BEFORE record() — secrets         │      │             ├──────────────────────────┤
  never enter the buffer     record()      └────────────▶│ SharinganExport          │
                                                         │ agentMarkdown/curl/json/ │
                                                         │ sessionJson/summary      │
                                                         └───────────┬──────────────┘
                                                                     ▼
                                                  copyToClipboard / shareText
                                                  (expect/actual per platform)
```

Key files, in flow order:

| Stage | File |
|---|---|
| Facade (singleton store + loggers) | [sharingan/src/commonMain/kotlin/dev/sharingan/Sharingan.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/Sharingan.kt) |
| Ktor plugin (automatic HTTP) | [sharingan/src/commonMain/kotlin/dev/sharingan/ktor/SharinganKtor.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ktor/SharinganKtor.kt) |
| Manual loggers | `HttpLogger.kt`, `MqttLogger.kt`, `BleLogger.kt` in [sharingan/src/commonMain/kotlin/dev/sharingan/](../sharingan/src/commonMain/kotlin/dev/sharingan/) |
| Event models (sealed `SharinganEvent`) | `SharinganEvent.kt`, `HttpEvent.kt`, `MqttEvent.kt`, `BleEvent.kt` (same dir) |
| Ring buffer | [sharingan/src/commonMain/kotlin/dev/sharingan/SharinganStore.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/SharinganStore.kt) |
| Log browser entry | [sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganScreen.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganScreen.kt) |
| Exporters | [sharingan/src/commonMain/kotlin/dev/sharingan/SharinganExport.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/SharinganExport.kt) |
| Protocol descriptors (per-protocol UI/export/ticker knowledge) | [sharingan/src/commonMain/kotlin/dev/sharingan/ui/ProtocolDescriptor.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/ProtocolDescriptor.kt) + `HttpDescriptor.kt` / `MqttDescriptor.kt` / `BleDescriptor.kt` |
| Android notification | [sharingan/src/androidMain/kotlin/dev/sharingan/internal/CaptureNotification.kt](../sharingan/src/androidMain/kotlin/dev/sharingan/internal/CaptureNotification.kt) |
| iOS entry point | [sharingan/src/iosMain/kotlin/dev/sharingan/SharinganViewController.kt](../sharingan/src/iosMain/kotlin/dev/sharingan/SharinganViewController.kt) |

`SharinganStore` is plain `MutableStateFlow` + atomic `update {}` CAS — thread-safe with no locks, callable from any thread. Eviction is a `subList` tail copy once `capacity` (default 300) is exceeded. Paused events are dropped, not queued. Event ids are process-unique atomic counters ([internal/EventIds.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/internal/EventIds.kt)).

`SharinganScreen` is the entire UI: home (3 protocol tabs → search → chips → terminal rows), detail (timing waterfall / headers / syntax-colored JSON), modal share sheet, confirmation toast. There is **no navigation library** — "navigation" is two `rememberSaveable` strings (`protocolName`, `selectedId`) in `SharinganScreen.kt`.

---

## 2. Decision log

Each decision is verified against code; file references inline.

### 2.1 Two-artifact model (Chucker-style), not runtime flags or finer modules

`:sharingan-noop` re-declares the entire public API with empty bodies ([sharingan-noop/src/commonMain/kotlin/SharinganNoop.kt](../sharingan-noop/src/commonMain/kotlin/SharinganNoop.kt)). Release builds swap the dependency; call sites are unchanged and Compose/UI bytes never ship. Bootstrap commit `728817b` names the model explicitly.

- **Why the Ktor plugin lives inside the core artifact** (not a `:sharingan-ktor` satellite): the release swap must stay *one* substitution. A comment in [sharingan/build.gradle.kts](../sharingan/build.gradle.kts) records this: *"The Ktor plugin ships in the core artifact (Chucker model) so the release no-op swap stays a single dependency substitution."*
- **Accepted trade-off:** `ktor-client-core` is a (non-`api`) dependency of both artifacts even for MQTT/BLE-only consumers (see both `build.gradle.kts` files). Judged acceptable since most KMP apps already ship Ktor.
- **Event model classes stay real in the no-op** ([sharingan-noop/src/commonMain/kotlin/HttpEvent.kt](../sharingan-noop/src/commonMain/kotlin/HttpEvent.kt) etc. are verbatim copies, not stubs): app code that constructs events or `when`-matches on the sealed interface behaves identically in release. Only *capture, UI and export output* are inert (the no-op `SharinganStore.record()` ignores input; `SharinganExport` returns `""`).
- The no-op `SharinganKtor` is a real `ClientPlugin` that installs cleanly and registers **zero hooks** ([sharingan-noop/src/commonMain/kotlin/ktor/SharinganKtor.kt](../sharingan-noop/src/commonMain/kotlin/ktor/SharinganKtor.kt)) — zero request-path cost in release.
- `coroutines-core` is `api` in both modules because `StateFlow` appears in the public surface (`Sharingan.events`).

### 2.2 Zero-setup Android init: manifest-merged ContentProvider

[sharingan/src/androidMain/kotlin/dev/sharingan/internal/SharinganAndroid.kt](../sharingan/src/androidMain/kotlin/dev/sharingan/internal/SharinganAndroid.kt) declares `SharinganInitProvider`, registered in the [library manifest](../sharingan/src/androidMain/AndroidManifest.xml) with `authorities="${applicationId}.sharingan-init"`. `ContentProvider.onCreate()` runs before `Application.onCreate()`, captures the application context into `SharinganAndroid.appContext`, and starts `CaptureNotification`. This is the androidx.startup trick **without the androidx.startup dependency** — consumers add the Gradle line and get the notification with no code.

### 2.3 Minimal-footprint rules

All deliberate, all verifiable:

| Choice | Evidence |
|---|---|
| No `androidx.core` — framework `Notification.Builder` with an API-26 channel guard | `CaptureNotification.kt` lines around `Build.VERSION.SDK_INT >= O`; commit `615fde8` ("avoid forcing compileSdk 37 on consumers") |
| `activity-compose` pinned to **1.11.0** | [gradle/libs.versions.toml](../gradle/libs.versions.toml); newer versions pull androidx releases requiring compileSdk 37 |
| No icon font / material-icons artifact — hand-built `ImageVector` strokes | [ui/SharinganIcons.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganIcons.kt) (doc comment states the rationale) |
| No bundled IBM Plex — platform monospace face | [ui/SharinganTheme.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganTheme.kt): `MonoFont = FontFamily.Monospace` ("~200 KB per weight" comment); commit `b433cb9` lists it as a deliberate deviation from the prototype |
| No navigation library — plain state in `SharinganScreen.kt` | two `rememberSaveable` vars drive home/detail |
| No kotlinx-serialization — hand-rolled JSON pretty-printer + escaper | [ui/JsonPretty.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/JsonPretty.kt) (recursive-descent tokenizer), `jsonEscape` in [Format.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/Format.kt) |
| No image assets — brand mark drawn with `Canvas` | [ui/SharinganMark.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganMark.kt) |
| No DI framework | `Sharingan` object wires the singleton graph by hand |

### 2.4 Security & robustness

- **Redaction at capture time, not render time.** `HttpLogger.redact()` masks values (`••••`) for `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` (case-insensitive, configurable) *before* `store.record()` — secrets never enter the buffer, so every downstream consumer (UI, exports, notification ticker) is automatically safe. See [HttpLogger.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/HttpLogger.kt).
- **Bodies capped** at `maxBodyBytes` (default 64 KB) with an explicit truncation marker (`truncate()` in `ktor/SharinganKtor.kt`).
- **Streaming never consumed.** `shouldReadBody()` refuses `text/event-stream` and anything non-textual (`isTextual()` whitelist: `text/*`, `*json`, `*xml`, form-urlencoded) — SSE and binary downloads keep streaming for the caller.
- **Transport failures recorded then rethrown untouched** (catch block in the plugin's `on(Send)`): the app's error handling is never altered.
- **Notification failures swallowed.** `manager.notify()` is wrapped in `try/catch(Exception)` in `CaptureNotification.post()`. This is a scar, not paranoia: commit `8550bbd` records a real crash where the API-26 version guard left the builder chain (`.setSmallIcon` etc.) attached only to the `else` branch, so API 26+ posted an icon-less notification and the resulting `IllegalArgumentException` from a background flow collector killed the host app. Rule extracted: **a debug tool must never crash the host app.**
- **Memory-only ring buffer.** No disk, no network. Process death clears everything (a stated property, see §6).

### 2.5 UI architecture

- **Stateless `*Content` + thin state wrapper.** `SharinganScreen` (public) collects flows, owns selection/search/chip/share state, and forwards a `HomeUiState` + lambdas into `SharinganScreenContent` (internal, pure parameters). Same split inside: `HomeScreenContent` ([ui/HomeScreen.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/HomeScreen.kt)), `DetailScreenContent` ([ui/DetailScreen.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/DetailScreen.kt)), `ShareSheetBody` ([ui/ShareSheet.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/ShareSheet.kt)).
- **Design tokens lifted verbatim from the design handoff.** `SharinganColors` light/dark palettes in [ui/SharinganTheme.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganTheme.kt), exposed via `LocalSharinganColors`; a minimal Material3 scheme is derived only for sheets/ripples. Per-event presentation (method/status/direction/operation tints, rail color, row strings) is resolved once per event via `presentationOf()` in [ui/EventPresentation.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/EventPresentation.kt), which delegates to the event's ProtocolDescriptor (§5.1).
- **Scaffold + safeDrawing insets.** `SharinganScreenContent` roots in `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`, child applies `padding(innerPadding)` then `consumeWindowInsets(innerPadding)`; `SharinganActivity` calls `enableEdgeToEdge()`.
- **Previews only on stateless composables**, `private`, named `<Composable>_<Variant>Preview`, fake state from [ui/PreviewData.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/PreviewData.kt) (hardcoded IoT events mirroring the design). The VM-wrapper-equivalent (`SharinganScreen`) has no preview.
- Reference screenshots of the shipped UI live in [docs/screenshots/](screenshots/).

### 2.6 expect/actual inventory

| Declaration | File (common) | Android actual | iOS actual |
|---|---|---|---|
| `currentTimeMillis()` | [internal/Platform.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/internal/Platform.kt) | `System.currentTimeMillis()` | `NSDate().timeIntervalSince1970 * 1000` |
| `formatClockTime(Long)` → `HH:mm:ss.SSS` | same | `SimpleDateFormat`, `Locale.US` | cached `NSDateFormatter`, `en_US_POSIX` |
| `copyToClipboard(String)` | [internal/PlatformActions.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/internal/PlatformActions.kt) | `ClipboardManager.setPrimaryClip` (context from `SharinganAndroid.appContext`) | `UIPasteboard.generalPasteboard` |
| `shareText(String)` | same | `Intent.ACTION_SEND` chooser with `FLAG_ACTIVITY_NEW_TASK` | `UIActivityViewController` on main queue, presented from the topmost VC, with iPad popover anchor |
| `PlatformBackHandler(enabled, onBack)` | [ui/PlatformBackHandler.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/PlatformBackHandler.kt) | delegates to `androidx.activity.compose.BackHandler` (system back pops detail) | no-op — the in-UI Back button is the iOS-conventional path |

Actuals: `*.android.kt` under [sharingan/src/androidMain/.../internal/](../sharingan/src/androidMain/kotlin/dev/sharingan/internal/) and `*.ios.kt` under [sharingan/src/iosMain/.../internal/](../sharingan/src/iosMain/kotlin/dev/sharingan/internal/).

### 2.7 Platform parity philosophy

Identical capture API, identical screens, identical exports on both platforms. Divergence is limited to the *entry point*:

- **Android**: sticky silent notification (per-protocol counters, 3-event expanded ticker, Pause/Resume action, tap → `SharinganActivity`). Plus `Sharingan.show(context)` / `Sharingan.setNotificationEnabled(false)` ([androidMain/.../SharinganAndroid.kt](../sharingan/src/androidMain/kotlin/dev/sharingan/SharinganAndroid.kt)).
- **iOS**: `SharinganViewController(): UIViewController` (a `ComposeUIViewController` wrapping `SharinganScreen`) — present however the host likes. iOS has no sticky-notification mechanism a library can ship; a Live Activity requires an ActivityKit **widget extension**, which can only live in the host app target, never in a Kotlin library. The app-side recipe for that is in [docs/RECIPES.md](RECIPES.md) ("Live Activity analog").

---

## 3. Testing strategy

The store, models, exporters, plugin, filters and JSON printer were all TDD'd (commit messages record the red→green counts: 19 + 13 + 8 + 17 tests across commits `3855d16`, `a1c61fd`, `e81b20c`, `b433cb9`). All tests live in `commonTest` and run on both JVM and Kotlin/Native:

| Layer | Test file | What it pins |
|---|---|---|
| Ring buffer | [SharinganStoreTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/SharinganStoreTest.kt) | insertion order, eviction, pause/resume/clear |
| Models / URL parsing | [HttpEventTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/HttpEventTest.kt) | host/path derivation (query kept, port kept, `/` fallback), `isFailure` semantics |
| Loggers | [LoggersTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/LoggersTest.kt) | MQTT directions, BLE operations, header redaction, id uniqueness |
| Exporters | [SharinganExportTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/SharinganExportTest.kt) | Markdown shape, cURL shell-escaping, JSON escaping, session wrappers, byte formatting |
| Ktor plugin | [ktor/SharinganKtorTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ktor/SharinganKtorTest.kt) | via **MockEngine**: capture, redaction, truncation marker, failure propagation, downstream body still readable, paused = nothing recorded |
| Filter logic | [ui/EventFilterTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ui/EventFilterTest.kt) | chip semantics per protocol, case-insensitive search haystacks |
| Share routing | [ui/ShareResolverTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ui/ShareResolverTest.kt) | action × scope × event-type → payload/delivery/toast decision table |
| Notification wording | [internal/NotificationContentTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/internal/NotificationContentTest.kt) | title/counters/ticker/action text, nothing-to-post case |
| JSON pretty-printer | [ui/JsonPrettyTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ui/JsonPrettyTest.kt) | indentation, escapes, `null` fallback on invalid/trailing-garbage input |

**Run:**

```bash
./gradlew :sharingan:testDebugUnitTest        # JVM (Android unit)
./gradlew :sharingan:iosSimulatorArm64Test    # Kotlin/Native
```

**Conventions & constraints:**

- Names follow `` `Given …, When …, Then …` `` BDD phrasing — but **without commas**: Kotlin/Native rejects backticked test names containing commas (and other invalid-identifier chars) when generating native test symbols. Write `Given a full buffer When another event is recorded Then…`, not `Given a full buffer, When…`. Every existing test follows this.
- Use a fresh `SharinganStore(capacity)` per test, never the `Sharingan` singleton — keeps tests isolated.
- **Composables are not unit-tested** — they are covered by `@Preview`s on every stateless `*Content` (visual verification) plus the on-device instrumented suites below, which landed once the flows settled (per project convention).
- **On-device UI tests** (needs a connected device or emulator):
  - `./gradlew :sharingan:connectedDebugAndroidTest` — [SharinganScreenUiTest](../sharingan/src/androidInstrumentedTest/kotlin/dev/sharingan/ui/SharinganScreenUiTest.kt): the full browser flow (tab counts, descriptor chips, search, detail sections, share sheet, copy-for-agent toast, REC pause) via the Compose Multiplatform test API (`runComposeUiTest`), each test against its own seeded `SharinganStore`. The test APK registers `ComponentActivity` in [androidInstrumentedTest/AndroidManifest.xml](../sharingan/src/androidInstrumentedTest/AndroidManifest.xml) instead of pulling androidx's `ui-test-manifest` (avoids pinning a second androidx-compose version next to CMP's).
  - `./gradlew :sample:composeApp:connectedDebugAndroidTest` — [CaptureNotificationE2eTest](../sample/composeApp/src/androidInstrumentedTest/kotlin/dev/sharingan/sample/CaptureNotificationE2eTest.kt): zero-setup init → capture notification with per-protocol counters → Paused/Capturing toggle, asserted via the app's own `activeNotifications` (immune to DND, no shade automation).
  - Instrumented test names use plain `flow_expectation` style — backticked names with spaces are unreliable after dexing on older APIs.

---

## 4. Build & toolchain

Versions ([gradle/libs.versions.toml](../gradle/libs.versions.toml)): Kotlin **2.4.0**, AGP **8.13.2**, Compose Multiplatform **1.11.1**, Ktor **3.5.0**, coroutines **1.11.0**, activity-compose **1.11.0**; compileSdk/targetSdk 36, minSdk 24, JVM target 17.

Quirks and facts a maintainer needs:

- **iOS targets are `iosArm64` + `iosSimulatorArm64` only.** CMP 1.11 dropped `iosX64`; don't try to add it back.
- **AGP 8.13's bundled lint cannot read Kotlin 2.4 metadata.** The sample disables release-lint: `lint.checkReleaseBuilds = false` in [sample/composeApp/build.gradle.kts](../sample/composeApp/build.gradle.kts) (the comment in that file is the record). Revisit when AGP catches up.
- **`explicitApi()` mode** is on in both library modules — every declaration needs an explicit visibility modifier and public members need explicit return types. The compiler is your API-surface linter.
- **API-parity proof:** the sample selects its dependency at configuration time — `-Psharingan.noop` substitutes `:sharingan-noop` for `:sharingan` in the *same* `commonMain` dependency list (see the `providers.gradleProperty("sharingan.noop")` block in the sample's build file). `./gradlew :sample:composeApp:assembleRelease -Psharingan.noop` therefore compiles every real call site against the no-op surface; if it builds, parity holds.
- **Publishing** is plain `maven-publish` (no signing, no Central config). `./gradlew publishToMavenLocal` works today; Maven Central publishing (signing, POM metadata, Sonatype) is **not yet configured**.
- The Android library publishes the `release` variant only (`publishLibraryVariants("release")`); `consumer-rules.pro` exists but is currently empty.
- Sample install: `./gradlew :sample:composeApp:installDebug`. There is **no checked-in Xcode project** for the sample's iOS side — only `MainViewController()` ([sample/composeApp/src/iosMain/.../MainViewController.kt](../sample/composeApp/src/iosMain/kotlin/dev/sharingan/sample/MainViewController.kt)) for a host to wrap.

---

## 5. Extension guide

> **THE invariant: every public API addition to `:sharingan` MUST be mirrored in `:sharingan-noop` with an inert twin** (same signature, defaults, package; empty body / `""` / empty controller). Verify with:
> ```bash
> ./gradlew :sample:composeApp:assembleRelease -Psharingan.noop
> ```
> If the sample exercises the new API (add a call in `DemoTraffic.kt`/`App.kt` if not), a green build *is* the parity proof. This is how every existing API landed (commit `70017b9`).

### 5.1 Add a new protocol tab (e.g. WebSocket, gRPC)

Per-protocol knowledge lives in one place: a **ProtocolDescriptor** ([ui/ProtocolDescriptor.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/ProtocolDescriptor.kt)) — chips, chip matching, search haystack, row presentation, the notification ticker line, the per-event export fragments and the `@Composable` detail body. `descriptorOf(event)` is the single exhaustive `when (event)` in the codebase, so adding a sealed subtype produces a compile error at exactly one registration site, and the descriptor base class then forces every concern (including the detail body) to be implemented:

1. **Event model**: new `data class XxxEvent(...) : SharinganEvent` next to [HttpEvent.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/HttpEvent.kt); override `isFailure` if failure isn't just `error != null`.
2. **Logger**: `XxxLogger(store)` modeled on [MqttLogger.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/MqttLogger.kt); expose on the [Sharingan.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/Sharingan.kt) facade. Use `EventIds.next("xxx-")`.
3. **Descriptor**: `internal object XxxDescriptor : ProtocolDescriptor<XxxEvent>()` modeled on [ui/MqttDescriptor.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/MqttDescriptor.kt); add the tab to the `Protocol` enum ([ui/EventFilter.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/EventFilter.kt)) and register the descriptor in both `descriptorOf()` overloads. TDD chips/search against [ui/EventFilterTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ui/EventFilterTest.kt), exports against `SharinganExportTest.kt` — both suites test through the stable shells (`matchesChip`, `SharinganExport.agentMarkdown`, …), which delegate to descriptors.
4. **Residual UI chrome**: tab icon in [ui/SharinganIcons.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/SharinganIcons.kt) and the icon `when` in `TabBar` ([ui/HomeScreen.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/HomeScreen.kt)); search placeholder in `SearchField`; session `countsLine` in [SharinganExport.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/SharinganExport.kt) (session assembly deliberately stays outside descriptors).
5. **Noop mirror**: copy the event model verbatim + inert logger into [sharingan-noop/src/commonMain/kotlin/](../sharingan-noop/src/commonMain/kotlin/); run the parity build. (Descriptors are `internal` — the noop never mirrors them.)
6. Preview data in [ui/PreviewData.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/PreviewData.kt) and demo traffic in [sample .../DemoTraffic.kt](../sample/composeApp/src/commonMain/kotlin/dev/sharingan/sample/DemoTraffic.kt).

For **WebSocket capture** specifically: Ktor's `WebSockets` plugin offers no equivalent of `on(Send)` interception per frame, so either wrap `DefaultClientWebSocketSession` or document manual logging (`Sharingan.ws.sent/received`) like MQTT. For **gRPC**: there is no standard KMP gRPC client; manual logger is the consistent choice (see §6, last bullet).

### 5.2 Add a share format

1. Formatter in [SharinganExport.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/SharinganExport.kt) (public — the export API is a feature; tests first). Per-event fragments go on the descriptors; session assembly stays in `SharinganExport`.
2. New `ShareAction` enum case + sheet row in [ui/ShareSheet.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/ShareSheet.kt).
3. Payload + delivery + toast are one branch in `resolveShare()` ([ui/ShareResolver.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/ShareResolver.kt)), TDD against [ui/ShareResolverTest.kt](../sharingan/src/commonTest/kotlin/dev/sharingan/ui/ShareResolverTest.kt) — `SharinganScreen` needs no changes.
4. Mirror the formatter signature (returning `""`) in the noop `SharinganExport`.

### 5.3 Add row-style variants

The design explored three densities — **Terminal / Comfortable / Badged** — and only Terminal shipped (`TerminalRow` in [ui/HomeScreen.kt](../sharingan/src/commonMain/kotlin/dev/sharingan/ui/HomeScreen.kt); its doc comment names it "the design's default 'Terminal' density"). To add a variant: new `ComfortableRow`/`BadgedRow` composable beside `TerminalRow` consuming the same `EventPresentation`, a density value in `HomeUiState`, and a row-style picker in `HomeHeader`. `EventPresentation` already carries everything (`sub`, `sizeLabel`, tints) the richer variants need.

### 5.4 Persist events

Today the buffer is deliberately memory-only. If persistence is ever wanted: keep `SharinganStore` as the hot interface and add an optional sink observing `store.events` (the pattern `CaptureNotification.start()` already uses); never make persistence the source of truth. Mind: redaction already happened at capture, but bodies may still hold PII — persisting changes the security posture, opt-in only.

### 5.5 Sample iosApp Xcode project

Add `sample/iosApp/` (standard KMP template: `iosApp.xcodeproj` + SwiftUI `ContentView` wrapping `MainViewController()` from the `ComposeApp` framework). The Kotlin side is already done — `MainViewController()` and `presentSharingan()` exist in [sample .../MainViewController.kt](../sample/composeApp/src/iosMain/kotlin/dev/sharingan/sample/MainViewController.kt); the framework (`baseName = "ComposeApp"`, static) is configured in the sample's build file.

---

## 6. Known limitations / gotchas

- **`SharinganScreen()` exists only in the debug artifact.** It is the one asymmetry in the API surface (deliberate: a noop composable would drag Compose into the release artifact). Never reference it from code compiled against the noop; use `Sharingan.show(context)` / `SharinganViewController()`, which *are* mirrored. Documented in [AGENTS.md](../AGENTS.md) and README §"Release builds".
- **DND hides the notification.** The channel is `IMPORTANCE_LOW` and the notification silent (`CaptureNotification.ensureChannel`), so Do Not Disturb suppresses it on most devices. `Sharingan.show(context)` always works.
- **`POST_NOTIFICATIONS` must be requested by the host app** on Android 13+. The library only *declares* the permission ([sharingan/src/androidMain/AndroidManifest.xml](../sharingan/src/androidMain/AndroidManifest.xml)); the sample shows the request ([MainActivity.kt](../sample/composeApp/src/androidMain/kotlin/dev/sharingan/sample/MainActivity.kt)). Without the grant, capture still works (`areNotificationsEnabled()` check + swallowed `notify()` failures), there's just no notification.
- **Buffer lost on process death** — memory-only by design; capacity is per-store (`SharinganStore(capacity = …)`).
- **The notification observer starts once per process** (`CaptureNotification.start` guards on `scope != null`) and survives for the process lifetime; `setNotificationEnabled(false)` cancels the posted notification but keeps observing.
- **`HttpEvent.host/path` come from a naive string split** (`splitUrl` in `HttpEvent.kt`), not a URL parser — fine for display, don't reuse it for anything semantic.
- **Request-body capture only sees `OutgoingContent.ByteArrayContent`** (`outgoingBodyText` in `ktor/SharinganKtor.kt`); streamed/chunked request bodies are not captured (responses: textual-only whitelist, SSE never read).
- **MQTT/BLE capture is manual by design.** There is no dominant KMP MQTT or BLE client to hook the way Ktor is hooked, so the API is one-line logger calls from whatever client's callbacks ([docs/RECIPES.md](RECIPES.md) has KMQTT/HiveMQ/Paho and Kable adapters). Resist building adapters into the core artifact — that would add the very dependencies the two-artifact model avoids.
- **Timing waterfall granularity differs by source**: the Ktor plugin only measures TTFB/Download (wall-clock around `proceed()`); the full DNS/Connect/TLS phases appear only when supplied via `Sharingan.http.log(timing = …)` (preview data shows the intended rendering).
