# Sharingan iOS-Readiness — Design

**Date:** 2026-06-12
**Origin:** Field test by an independent agent acting as a first-time iOS consumer
(report: `~/sharingan-ios-consumer-test/FEEDBACK.md`), plus a crash investigation
that followed its visual verification run.

## Problem

The iOS consumer journey fails in three independent ways:

1. **Host-app crash.** `SharinganViewController()` wraps `ComposeUIViewController {}`
   with no configuration. Compose Multiplatform's `PlistSanityCheck` throws when the
   host app's Info.plist lacks `CADisableMinimumFrameDurationOnPhone`, and on
   Kotlin/Native an unhandled exception on a dispatch queue means
   `terminateWithUnhandledException` → SIGABRT. Confirmed via `.ips` crash reports
   from the field test (three crashes, faulting frame
   `androidx.compose.ui.uikit.PlistSanityCheck`).
2. **Docs teach an integration that cannot compile.** The README shows
   `implementation("dev.sharingan:sharingan:0.1.0")` and a Swift call to
   `SharinganViewControllerKt.SharinganViewController()`. Kotlin/Native framework
   headers only export symbols reachable at `api` depth *and* declared via
   `export(...)` in the consumer's `binaries.framework` block. Following the docs
   verbatim yields a symbol that does not exist in `shared.h`.
3. **Onboarding gaps.** No mavenLocal step (Maven Central is not configured), no
   Xcode wiring guidance (search paths, linker flags, build-phase script), no
   warning that the `-Prelease` noop toggle must reach the Xcode build phase
   (otherwise the debug framework silently overwrites the noop one), no version
   matrix, no note that consumers do not need Compose dependencies, and no path at
   all for pure-Swift (non-KMP) apps. Measured cost: ~55 minutes to first captured
   event for an expert; realistic failure for a typical iOS developer.

## Scope decisions (made with the user)

- **XCFramework distribution: in scope.** Built locally via a Gradle task and
  embedded manually; SPM + GitHub Releases automation is roadmap, not this pass.
- **Swift API surface: minimal.** Viewer entry point + a `presentSharingan()`
  convenience. No runtime bridge object (events/isRecording/clear stay Kotlin-only).
- **Crash handling: library-side.** Disable CMP's strict plist check rather than
  documenting the key as a requirement. Rationale: a debug tool must never crash
  the host app (consistent with the existing notification-hardening principle).

## Design

### 1. Library changes (`:sharingan` and `:sharingan-noop`, iosMain)

- `SharinganViewController()` becomes
  `ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) { SharinganScreen() }`.
  The plist key is documented as an optional ProMotion/120Hz improvement only.
- New `presentSharingan(animated: Boolean = true)` in iosMain: resolves the topmost
  view controller (key window root → `presentedViewController` chain) and presents
  the viewer modally. This promotes the sample app's private `presentSharingan()`
  into library API.
- File-level `@ObjCName` so the Swift surface reads as a stable, intention-revealing
  class name rather than `SharinganIosKt`. The exact generated names are pinned
  during implementation by inspecting the produced framework header, and the
  documented Swift snippets must match that header character-for-character.
- `:sharingan-noop` mirrors both symbols with inert bodies (empty
  `UIViewController`, no-op present) so debug/noop swaps remain compile-identical.
- **Testing:** topmost-VC resolution logic gets Given/When/Then unit tests in
  `iosSimulatorArm64Test` (UIKit classes instantiate in simulator test runs).
  The `configure` flag and `@ObjCName` are platform wiring (TDD-exempt); they are
  verified by the end-to-end re-test instead.

### 2. XCFramework distribution

- Both modules gain the Kotlin Gradle `XCFramework` DSL: static frameworks,
  `baseName = "Sharingan"` and `"SharinganNoop"`, targets iosArm64 +
  iosSimulatorArm64. Build command: `./gradlew assembleSharinganXCFramework`
  (and the noop counterpart).
- The two frameworks expose an identical header surface, so pure-Swift consumers
  switch debug/noop per build configuration with
  `FRAMEWORK_SEARCH_PATHS[config=Release]` pointing at the noop output — no code
  changes, and no silent-overwrite hazard on this path.
- Adding `binaries.framework`/XCFramework output is additive; Maven publishing of
  the existing artifacts is unaffected.

### 3. Documentation overhaul (README, docs/RECIPES.md, site, AGENTS.md + llms.txt)

- **KMP path (corrected):** consumer `shared` module uses `api(...)` and
  `binaries.framework { export("dev.sharingan:sharingan:0.1.0") }` (noop variant
  exported under the release toggle). Swift snippets show the real generated names.
- **New "iOS setup" walkthrough:** clone + `./gradlew publishToMavenLocal` +
  `mavenLocal()` repository (explicitly framed as the pre-Maven-Central path);
  Xcode `FRAMEWORK_SEARCH_PATHS`, `OTHER_LDFLAGS`, the Gradle build-phase script;
  how `baseName` maps to the Swift `import`; an explicit warning box that the
  noop toggle property must be passed inside the Xcode build phase or the debug
  framework silently replaces the noop one.
- **New "Pure Swift app" section:** build the XCFramework, drag into Xcode,
  embed & sign, per-configuration search-path swap for noop.
- Version compatibility matrix (Sharingan 0.1.0 → Kotlin 2.4.0, Ktor 3.5+,
  Compose Multiplatform 1.11+); note that HTTP-capture-only consumers need no
  Compose dependencies in their own module; optional plist key note.
- Ride-along: replace deprecated string-based Compose DSL accessors in the
  library's own build scripts so consumers don't copy deprecation warnings.

### 4. Verification (delegated, supervised)

The independent agent (OpenCode #2) re-runs the consumer journey after each
deliverable lands, following only the updated docs:

1. KMP consumer app: remove all hand-rolled workarounds, re-integrate per docs.
   Success = zero undocumented steps.
2. Visual pass on the booted iPhone 17 Pro simulator: the viewer must render
   (screenshots inspected by the supervising agent), with zero new `.ips`
   termination reports for the app.
3. Fresh pure-Swift app exercising the XCFramework path end to end.

### 5. Commit plan (deliverable-shaped)

1. **"iOS viewer presents safely in any host app"** — crash fix +
   `presentSharingan()` + `@ObjCName` surface + noop mirrors + iosTest coverage.
2. **"Pure-Swift apps can embed Sharingan"** — XCFramework assembly for both
   artifacts.
3. **"Docs-only onboarding reaches first captured event on iOS"** — documentation
   overhaul across README/RECIPES/site/AGENTS.md/llms.txt + DSL deprecation
   cleanup.
4. Fixes arising from the field re-test, if any.

## Out of scope (recorded for roadmap)

- SPM package / GitHub Releases hosting of the XCFramework.
- Maven Central publishing.
- Swift runtime bridge (events, isRecording, clear from Swift).
- In-viewer navigation hooks for UI automation (the field test could not reach
  the detail screen programmatically; revisit alongside E2E tooling).
