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
  (API verified by compile probe against CMP 1.11.1 on 2026-06-12.) The docs
  describe the plist key accurately: *in CMP 1.11.1, omitting
  `CADisableMinimumFrameDurationOnPhone` crashes the host app unless the strict
  check is disabled; Sharingan disables it library-side, and adding the key
  remains a valid host-side alternative (and enables ProMotion/120Hz).*
- New `presentSharingan(animated: Boolean = true)` in iosMain: hops to the main
  queue (`dispatch_async(dispatch_get_main_queue())`) so it is safe from any
  thread, resolves the topmost view controller (key window via `isKeyWindow` —
  not the deprecated `keyWindow` — root → `presentedViewController` chain,
  skipping transient overlays like `UIAlertController`/`UIActivityViewController`),
  and presents the viewer modally. This promotes the sample app's private
  `presentSharingan()` into library API, hardened.
- File-level `@ObjCName` so the Swift surface reads as a stable, intention-revealing
  class name rather than `SharinganIosKt`. This requires
  `@file:OptIn(ExperimentalObjCName::class)` plus
  `languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")` in both
  modules' build scripts — an explicit API-surface decision; note the rationale in
  the commit body. The exact generated names are pinned during implementation by
  inspecting the produced framework header; documented Swift snippets are
  placeholders until then and must end up matching that header
  character-for-character.
- `:sharingan-noop` mirrors both symbols with inert bodies (empty
  `UIViewController`, no-op present) so debug/noop swaps remain compile-identical.
- **Testing:** topmost-VC resolution logic gets Given/When/Then unit tests in
  `iosSimulatorArm64Test` (UIKit classes instantiate in simulator test runs
  without a running UI — tests build an in-memory window/VC tree). Multi-window
  iPad scene resolution is documented as manually-verified-only; XCTest in the
  simulator provides no multi-scene host. The `configure` flag and `@ObjCName`
  are platform wiring (TDD-exempt); they are verified by the end-to-end re-test
  instead.

### 2. XCFramework distribution

- Both modules gain the Kotlin Gradle `XCFramework` DSL: static frameworks,
  **`baseName = "Sharingan"` in both modules** — the per-configuration swap only
  works if `import Sharingan` resolves in every configuration, so the noop
  differs by output directory, not by module name. Targets iosArm64 +
  iosSimulatorArm64. The DSL registers variant-suffixed tasks; the documented
  build commands are `./gradlew :sharingan:assembleSharinganReleaseXCFramework`
  and `./gradlew :sharingan-noop:assembleSharinganReleaseXCFramework`.
- The two frameworks must expose an identical header surface. This is asserted,
  not assumed: §4 adds a programmatic header diff. Pure-Swift consumers then
  switch debug/noop per build configuration. Search paths alone are
  insufficient — the docs must cover the embed-and-sign side too: keep both
  XCFrameworks in configuration-specific directories, set
  `FRAMEWORK_SEARCH_PATHS` per configuration, **and** either use a
  per-configuration Embed Frameworks phase or a single output directory whose
  contents a build-phase script swaps, so the embedded binary matches the linked
  one (a mismatch produces a dyld crash at launch).
- Adding `binaries.framework`/XCFramework output is additive; Maven publishing of
  the existing artifacts is unaffected. Docs warn against mixing integration
  paths (Maven dependency in a KMP shared module *and* the XCFramework in the
  same app) — pick one.
- Known cosmetic issue to document: a debug framework is ~240 MB on disk
  (Kotlin/Native runtime + Compose, not Sharingan code); release/noop links far
  smaller and the size never ships to users at that magnitude.

### 3. Documentation overhaul (README, docs/RECIPES.md, site, AGENTS.md + llms.txt)

- **KMP path (corrected):** consumer `shared` module declares the dependency as
  `api(...)` **in `iosMain.dependencies`** (export() resolves against the
  corresponding source set's api configuration — `commonMain` placement is the
  most likely silent-empty-header mistake) plus
  `binaries.framework { export("dev.sharingan:sharingan:0.1.0") }` on each iOS
  target (noop variant exported under the release toggle). Swift snippets show
  the real generated names. The recipe is validated end-to-end by §4 before the
  docs ship.
- **New "iOS setup" walkthrough:** clone + `./gradlew publishToMavenLocal` +
  `mavenLocal()` repository (explicitly framed as the pre-Maven-Central path);
  Xcode `FRAMEWORK_SEARCH_PATHS`, `OTHER_LDFLAGS`, the Gradle build-phase script;
  how `baseName` maps to the Swift `import`; an explicit warning box that the
  noop toggle property must be passed inside the Xcode build phase or the debug
  framework silently replaces the noop one.
- **New "Pure Swift app" section:** build the XCFramework, drag into Xcode,
  embed & sign, per-configuration search-path swap for noop.
- Version compatibility matrix with exact tested pins — "Tested with Kotlin
  2.4.0, Ktor 3.5.0, Compose Multiplatform 1.11.1; later versions may work but
  are unverified (Kotlin/Native has no cross-compiler-version binary
  compatibility guarantee)" — not open-ended `+` ranges. Note that
  HTTP-capture-only consumers need no Compose dependencies in their own module;
  plist key note per §1 wording.
- Deprecated string-based Compose DSL accessors in the library's own build
  scripts move to a separate chore commit with a before/after dependency-
  resolution diff (not a ride-along), since the replacement can silently alter
  resolved versions.

### 4. Verification (delegated, supervised)

The independent agent (OpenCode #2) re-runs the consumer journey after each
deliverable lands, following only the updated docs:

1. KMP consumer app: remove all hand-rolled workarounds, re-integrate per docs.
   Success = zero undocumented steps.
2. Visual pass on the booted iPhone 17 Pro simulator: the viewer must render
   (screenshots inspected by the supervising agent), with zero new `.ips`
   termination reports for the app. Includes a landscape rotation check
   (no clipping / safe-area violations) and, where reachable, the event-detail
   and share-sheet surfaces.
3. Fresh pure-Swift app exercising the XCFramework path end to end.
4. Header-parity gate: programmatically diff the generated
   `Sharingan.framework/Headers/Sharingan.h` (and `module.modulemap`) from the
   `:sharingan` XCFramework against the `:sharingan-noop` one. With identical
   baseNames the files should match exactly; any difference must be reviewed
   and either eliminated or shown to be a documented debug-only symbol (e.g.
   the `SharinganScreen` composable, which deliberately exists only in the
   debug artifact). The noop has never been linked as a standalone framework
   before, so this gate is mandatory, not optional.

### 5. Commit plan (deliverable-shaped)

1. **"iOS viewer presents safely in any host app"** — crash fix +
   `presentSharingan()` + `@ObjCName` surface + noop mirrors + iosTest coverage.
2. **"Pure-Swift apps can embed Sharingan"** — XCFramework assembly for both
   artifacts.
3. **"Docs-only onboarding reaches first captured event on iOS"** — documentation
   overhaul across README/RECIPES/site/AGENTS.md/llms.txt.
4. Chore: Compose DSL deprecation cleanup in library build scripts (standalone,
   with dependency-resolution before/after diff).
5. Fixes arising from the field re-test, if any.

## Out of scope (recorded for roadmap)

- SPM package / GitHub Releases hosting of the XCFramework.
- Maven Central publishing.
- Swift runtime bridge (events, isRecording, clear from Swift).
- SwiftUI `.sharinganSheet()` modifier / `UIApplication` extension sugar
  (suggested in field-test feedback; `presentSharingan()` covers the need for
  this pass).
- In-viewer navigation hooks for UI automation (the field test could not reach
  the detail screen programmatically; revisit alongside E2E tooling).
- Android-side feedback items from the parallel Android field test (quickstart
  template / `sharingan init`, notification-permission doc polish,
  `dev.sharingan.show` import discoverability) — tracked for a follow-up pass;
  the mavenLocal walkthrough and version matrix in §3 are written
  platform-neutrally and resolve the overlapping Android doc gaps.
