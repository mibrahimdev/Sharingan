# Sharingan iOS-Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iOS consumer journey work as documented — the viewer never crashes the host app, a one-call `presentSharingan()` exists, both artifacts ship as XCFrameworks, and the docs alone take a first-time consumer to a captured event.

**Architecture:** Three library-code deliverables on the existing `:sharingan` / `:sharingan-noop` two-artifact model (crash-proof `ComposeUIViewController` config; a hardened topmost-VC presenter in `iosMain`; XCFramework outputs with identical `baseName` so pure-Swift apps swap debug/noop by search path), followed by a docs overhaul and a delegated field re-verification.

**Tech Stack:** Kotlin 2.4.0, Compose Multiplatform 1.11.1, Kotlin/Native ObjC export, Kotlin Gradle XCFramework DSL, XCTest-hosted `iosSimulatorArm64Test`.

**Spec:** `docs/superpowers/specs/2026-06-12-ios-readiness-design.md`

**Plan-time deviations from spec (with rationale):**
- The spec says `isKeyWindow`; the plan keeps `it.keyWindow`. Kotlin/Native interop exposes the ObjC property `keyWindow` (getter `isKeyWindow`) under the property name `keyWindow`, and **`UIWindow.keyWindow` is not deprecated** — the deprecated API is `UIApplication.keyWindow`, which we never touch. The existing sample (`sample/composeApp/src/iosMain/kotlin/dev/sharingan/sample/MainViewController.kt:20`) compiles warning-free with `it.keyWindow` today.
- The spec says present-time "skipping" of transient overlays (`UIAlertController` etc.). UIKit cannot present from a covered controller ("Attempt to present … which is already presenting"), so true skipping requires dismissing the overlay — too intrusive for a debug tool. The presenter walks to the true top and presents there (sheet over alert still shows and is dismissible). This matches the spec's never-fail intent; the m1 review finding was explicitly optional.

---

### Task 1: Topmost-VC resolution (TDD) + crash-proof viewer + `presentSharingan()`

**Deliverable:** After this commit, presenting the Sharingan viewer cannot crash a host app missing the plist key, and one call — `presentSharingan()` — shows the browser from any thread.

**Files:**
- Create: `sharingan/src/iosTest/kotlin/dev/sharingan/internal/TopmostViewControllerTest.kt`
- Create: `sharingan/src/iosMain/kotlin/dev/sharingan/internal/TopmostViewController.kt`
- Modify: `sharingan/src/iosMain/kotlin/dev/sharingan/SharinganViewController.kt`
- Modify: `sharingan-noop/src/iosMain/kotlin/SharinganViewController.kt`
- Modify: `sharingan/build.gradle.kts` (test device pin + opt-in)
- Modify: `sharingan-noop/build.gradle.kts` (opt-in)
- Modify: `sample/composeApp/src/iosMain/kotlin/dev/sharingan/sample/MainViewController.kt` (DRY ride-along: use the new library API)

- [ ] **Step 1.1: Pin the simulator device for iOS test runs**

In `sharingan/build.gradle.kts`, inside `kotlin { }`, replace the bare target declarations

```kotlin
    iosArm64()
    iosSimulatorArm64()
```

with

```kotlin
    iosArm64()
    iosSimulatorArm64 {
        // The default KGP simulator id may not exist in newer Xcodes; pin to
        // a device present on this machine (xcrun simctl list devices).
        testRuns.configureEach { deviceId = "iPhone 17 Pro" }
    }
```

- [ ] **Step 1.2: Write the failing tests**

Create `sharingan/src/iosTest/kotlin/dev/sharingan/internal/TopmostViewControllerTest.kt`. Note: Kotlin/Native forbids commas inside backticked test names — the Given/When/Then phrasing drops commas.

```kotlin
package dev.sharingan.internal

import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.timeIntervalSinceNow
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import kotlin.test.Test
import kotlin.test.assertSame

class TopmostViewControllerTest {

    @Test
    fun `When root has no presentations Then root is the topmost`() {
        val root = UIViewController(nibName = null, bundle = null)

        assertSame(root, topmostViewController(root))
    }

    @Test
    fun `Given a two-deep presented chain When resolving Then the deepest controller wins`() {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 640.0))
        val root = UIViewController(nibName = null, bundle = null)
        window.rootViewController = root
        window.makeKeyAndVisible()
        val first = UIViewController(nibName = null, bundle = null)
        val second = UIViewController(nibName = null, bundle = null)

        root.presentViewController(first, animated = false, completion = null)
        spinUntil { root.presentedViewController != null }
        first.presentViewController(second, animated = false, completion = null)
        spinUntil { first.presentedViewController != null }

        assertSame(second, topmostViewController(root))
    }

    private fun spinUntil(deadlineSeconds: Double = 2.0, condition: () -> Boolean) {
        val deadline = NSDate.dateWithTimeIntervalSinceNow(deadlineSeconds)
        while (!condition() && deadline.timeIntervalSinceNow > 0) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
    }
}
```

- [ ] **Step 1.3: Run the tests — expect compile failure (RED)**

Run: `./gradlew :sharingan:iosSimulatorArm64Test --tests "dev.sharingan.internal.*" 2>&1 | tail -20`
Expected: FAIL — `unresolved reference: topmostViewController`.

- [ ] **Step 1.4: Implement the walk**

Create `sharingan/src/iosMain/kotlin/dev/sharingan/internal/TopmostViewController.kt`:

```kotlin
package dev.sharingan.internal

import platform.UIKit.UIViewController

/**
 * The controller that can legally host a new modal presentation — UIKit
 * rejects presenting from any controller that is already presenting.
 */
internal fun topmostViewController(root: UIViewController): UIViewController {
    var top = root
    while (true) {
        top = top.presentedViewController ?: break
    }
    return top
}
```

- [ ] **Step 1.5: Run the tests — expect GREEN**

Run: `./gradlew :sharingan:iosSimulatorArm64Test --tests "dev.sharingan.internal.*" 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`, 2 tests passed. (If the simulator boots slowly the first run can take a few minutes.)

- [ ] **Step 1.6: Rewire the viewer entry point (crash fix + ObjC name + presenter)**

Replace the entire contents of `sharingan/src/iosMain/kotlin/dev/sharingan/SharinganViewController.kt` with:

```kotlin
@file:OptIn(ExperimentalObjCName::class)
@file:ObjCName("SharinganUI")

package dev.sharingan

import androidx.compose.ui.window.ComposeUIViewController
import dev.sharingan.internal.topmostViewController
import dev.sharingan.ui.SharinganScreen
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The Sharingan log browser as a `UIViewController` — embed or present it
 * however your app likes (sheet, push, debug menu). For the common case,
 * call [presentSharingan] instead.
 *
 * A host app whose Info.plist lacks `CADisableMinimumFrameDurationOnPhone`
 * must never crash (Compose Multiplatform 1.11 aborts on a strict plist
 * check otherwise); the key remains a host-side opt-in for ProMotion/120Hz.
 */
public fun SharinganViewController(): UIViewController = ComposeUIViewController(configure = {
    enforceStrictPlistSanityCheck = false
}) {
    SharinganScreen()
}

/**
 * Presents the log browser over the topmost view controller of the key
 * window. Safe to call from any thread; no-ops if no window is attached yet.
 *
 * ```swift
 * SharinganUI.presentSharingan(animated: true)
 * ```
 */
public fun presentSharingan(animated: Boolean = true) {
    dispatch_async(dispatch_get_main_queue()) {
        val root = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.keyWindow }
            ?.rootViewController ?: return@dispatch_async
        topmostViewController(root)
            .presentViewController(SharinganViewController(), animated = animated, completion = null)
    }
}
```

- [ ] **Step 1.7: Mirror in the noop**

Replace the entire contents of `sharingan-noop/src/iosMain/kotlin/SharinganViewController.kt` with:

```kotlin
@file:OptIn(ExperimentalObjCName::class)
@file:ObjCName("SharinganUI")

package dev.sharingan

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

/**
 * No-op twin: returns an empty view controller so Swift call sites compile
 * and run against the release framework without UI or Compose payload.
 */
public fun SharinganViewController(): UIViewController = UIViewController(nibName = null, bundle = null)

/** No-op twin: release builds have no browser to present. */
public fun presentSharingan(animated: Boolean = true) {
}
```

- [ ] **Step 1.8: Add the opt-in to both Gradle scripts (belt-and-braces alongside `@file:OptIn`)**

In `sharingan/build.gradle.kts` and `sharingan-noop/build.gradle.kts`, add as the first line inside `sourceSets { }`:

```kotlin
        all { languageSettings.optIn("kotlin.experimental.ExperimentalObjCName") }
```

Rationale to include in the commit body (explicit-API decision per project convention): *ObjC names are exported API surface; `@ObjCName` is the supported mechanism and its experimental status is acceptable for a debug tool's iOS facade.*

- [ ] **Step 1.9: DRY ride-along — sample delegates to the library presenter**

In `sample/composeApp/src/iosMain/kotlin/dev/sharingan/sample/MainViewController.kt`, delete the entire `private fun presentSharingan() { ... }` (lines 16–26) and its now-unused imports (`UIApplication`, `UIWindow`, `UIWindowScene`); add `import dev.sharingan.presentSharingan`. Line 13's `App(openSharingan = { presentSharingan() })` is unchanged and now resolves to the library function.

- [ ] **Step 1.10: Full verification sweep**

Run: `./gradlew :sharingan:iosSimulatorArm64Test :sharingan:compileKotlinIosArm64 :sharingan-noop:compileKotlinIosSimulatorArm64 :sample:composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.
Also run the existing suites to catch regressions: `./gradlew :sharingan:testDebugUnitTest :sharingan-noop:testDebugUnitTest 2>&1 | tail -5` → `BUILD SUCCESSFUL`.

- [ ] **Step 1.11: Commit**

```bash
git add sharingan/src/iosMain sharingan/src/iosTest sharingan-noop/src/iosMain \
        sharingan/build.gradle.kts sharingan-noop/build.gradle.kts \
        sample/composeApp/src/iosMain
git commit -m "iOS viewer presents safely in any host app

SharinganViewController() disables CMP's strict plist sanity check (a
missing CADisableMinimumFrameDurationOnPhone crashed hosts with SIGABRT,
confirmed by field-test .ips reports). New presentSharingan() presents
over the topmost view controller from any thread. @file:ObjCName exports
both under a stable SharinganUI Swift name — ObjC names are deliberate
public API; experimental ObjCName accepted for the iOS facade.
Noop mirrors both symbols; sample now uses the library presenter.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: XCFramework outputs for both artifacts + header-parity gate

**Deliverable:** After this commit, `./gradlew :sharingan:assembleSharinganReleaseXCFramework` and `./gradlew :sharingan-noop:assembleSharinganReleaseXCFramework` each produce a `Sharingan.xcframework` a pure-Swift app can embed, with verified interchangeable headers — and the real Swift symbol names are pinned for the docs task.

**Files:**
- Modify: `sharingan/build.gradle.kts`
- Modify: `sharingan-noop/build.gradle.kts`
- Create: `scripts/check-ios-header-parity.sh`

- [ ] **Step 2.1: Add the XCFramework DSL to `:sharingan`**

In `sharingan/build.gradle.kts`: add import at the top, then replace the iOS target declarations (as edited in Task 1) with the framework-producing form.

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
```

```kotlin
    // Same baseName in :sharingan and :sharingan-noop — pure-Swift consumers
    // swap debug/noop per build configuration by search path, so
    // `import Sharingan` must resolve identically in both.
    val sharinganXCFramework = XCFramework("Sharingan")
    iosArm64()
    iosSimulatorArm64 {
        testRuns.configureEach { deviceId = "iPhone 17 Pro" }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Sharingan"
            isStatic = true
            sharinganXCFramework.add(this)
        }
    }
```

(Calling `iosArm64()` twice is idiomatic — the second call configures the already-created target.)

- [ ] **Step 2.2: Same DSL in `:sharingan-noop`**

In `sharingan-noop/build.gradle.kts`: same import, and replace its `iosArm64()` / `iosSimulatorArm64()` lines with:

```kotlin
    val sharinganXCFramework = XCFramework("Sharingan")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Sharingan"
            isStatic = true
            sharinganXCFramework.add(this)
        }
    }
```

- [ ] **Step 2.3: Build both XCFrameworks**

Run: `./gradlew :sharingan:assembleSharinganReleaseXCFramework :sharingan-noop:assembleSharinganReleaseXCFramework 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`; outputs at `sharingan/build/XCFrameworks/release/Sharingan.xcframework` and `sharingan-noop/build/XCFrameworks/release/Sharingan.xcframework`.

- [ ] **Step 2.4: Pin the generated Swift names (feeds Task 3's docs)**

Run: `grep -n "SharinganUI\|SharinganViewController\|presentSharingan" sharingan/build/XCFrameworks/release/Sharingan.xcframework/ios-arm64/Sharingan.framework/Headers/Sharingan.h | head -20`
Expected: a `@interface` whose ObjC class is `SharinganUI` exposing `SharinganViewController` and `presentSharinganAnimated:` (or close variants). Record the exact Swift spellings — e.g. `SharinganUI.SharinganViewController()` and `SharinganUI.presentSharingan(animated:)` — in the next steps' doc snippets. If the names differ from these expectations, the doc snippets in Task 3 MUST be updated to the actual header names; do not document aspirational names.

- [ ] **Step 2.5: Write the header-parity gate**

Create `scripts/check-ios-header-parity.sh` (then `chmod +x` it):

```bash
#!/usr/bin/env bash
# Debug and noop must be drop-in interchangeable for Swift consumers:
# identical headers and module maps, differing only in implementation.
set -euo pipefail
cd "$(dirname "$0")/.."

REAL_FW="sharingan/build/XCFrameworks/release/Sharingan.xcframework/ios-arm64/Sharingan.framework"
NOOP_FW="sharingan-noop/build/XCFrameworks/release/Sharingan.xcframework/ios-arm64/Sharingan.framework"

fail=0
for f in "Headers/Sharingan.h" "Modules/module.modulemap"; do
  if diff -u "$REAL_FW/$f" "$NOOP_FW/$f"; then
    echo "OK: $f identical"
  else
    echo "MISMATCH: $f differs (see diff above)"
    fail=1
  fi
done
exit $fail
```

- [ ] **Step 2.6: Run the gate and adjudicate any diff**

Run: `./scripts/check-ios-header-parity.sh`
Expected: both files identical → exit 0. **If the header differs:** the only acceptable lines are symbols documented as debug-only (e.g. the `SharinganScreen` composable, if the ObjC exporter surfaces it). For each other diff line, add the missing public declaration to the noop module (mirror with an inert body, same signature) and rebuild until the diff contains only documented debug-only symbols. If diffs remain, append the adjudicated allowlist as a comment block at the bottom of the script with a one-line justification per symbol.

- [ ] **Step 2.7: Regression check — Maven publishing unaffected**

Run: `./gradlew publishToMavenLocal -q && ls ~/.m2/repository/dev/sharingan/ 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`; the same artifact list as before (sharingan, sharingan-android, sharingan-iosarm64, sharingan-iossimulatorarm64 + noop variants).

- [ ] **Step 2.8: Commit**

```bash
git add sharingan/build.gradle.kts sharingan-noop/build.gradle.kts scripts/check-ios-header-parity.sh
git commit -m "Pure-Swift apps can embed Sharingan: XCFramework output for both artifacts

Same baseName in debug and noop so import Sharingan resolves in every
build configuration; consumers swap by per-configuration search path.
scripts/check-ios-header-parity.sh gates that the two frameworks stay
drop-in interchangeable.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Documentation overhaul

**Deliverable:** After this commit, a first-time iOS consumer following only the docs reaches a captured event — every step the field test had to reverse-engineer is written down.

**Files:**
- Modify: `README.md`
- Modify: `docs/RECIPES.md`
- Modify: `AGENTS.md` and `llms.txt`
- Modify: `site/` getting-started/integration pages (mirror README content; locate with `grep -rln "implementation(\"dev.sharingan" site/`)

All Swift snippets below use the names pinned in Step 2.4 — substitute the actual header spellings if they differ.

- [ ] **Step 3.1: README — "Get the artifacts" subsection**

In `README.md`, insert directly under the `## Setup` heading (before "### Android app (two lines)"):

```markdown
### Get the artifacts

Sharingan is not yet on Maven Central. Until it is, publish locally:

```bash
git clone https://github.com/<org>/sharingan && cd sharingan
./gradlew publishToMavenLocal
```

and add `mavenLocal()` to your repositories (`settings.gradle.kts` →
`dependencyResolutionManagement { repositories { mavenLocal(); ... } }`).

**Tested versions:** Sharingan 0.1.0 → Kotlin **2.4.0**, Ktor **3.5.0**,
Compose Multiplatform **1.11.1**, AGP 8.13. Later versions may work but are
unverified — Kotlin/Native has no cross-compiler-version binary
compatibility guarantee, so match the Kotlin version exactly.
```

- [ ] **Step 3.2: README — fix the KMP framework recipe**

Replace the "### Kotlin Multiplatform app (shared module + iOS)" Gradle snippet (currently `commonMain.dependencies { if (providers.gradleProperty("release")...) }` at README.md:56-71) with:

````markdown
Gradle resolves one dependency list per source set, so pick the artifact with
a property — and note both iOS requirements: the dependency must be `api` **in
`iosMain`** (not `commonMain`), and the framework block must `export(...)` it.
Without both, Kotlin/Native generates an empty header and your Swift code
won't see Sharingan at all.

```kotlin
// shared/build.gradle.kts
kotlin {
    val sharinganArtifact = if (providers.gradleProperty("release").isPresent)
        "dev.sharingan:sharingan-noop:0.1.0" else "dev.sharingan:sharingan:0.1.0"

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            export(sharinganArtifact)   // surfaces Sharingan in shared.h
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(sharinganArtifact)
        }
        iosMain.dependencies {
            api(sharinganArtifact)      // export() requires api at THIS source set
        }
    }
}
```

> ⚠️ **The `release` property must reach every build that produces the
> framework Xcode links** — including the Gradle invocation inside your Xcode
> build phase (`./gradlew :shared:embedAndSignAppleFrameworkForXcode
> -Prelease`). If the flag is missing there, a debug framework silently
> overwrites your noop one.
````

- [ ] **Step 3.3: README — rewrite the iOS usage section**

Replace the Swift snippet in the "### iOS" section (README.md:153-166) with:

````markdown
```swift
import SwiftUI
import shared // your shared framework

// One-call presentation (topmost view controller, any thread):
SharinganUI.presentSharingan(animated: true)

// Or embed/present the view controller yourself:
struct SharinganView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SharinganUI.SharinganViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
.sheet(isPresented: $showLogs) { SharinganView() }
```

Two iOS notes:

- **No plist key needed.** Compose Multiplatform 1.11 crashes host apps whose
  Info.plist lacks `CADisableMinimumFrameDurationOnPhone`; Sharingan disables
  that strict check so the viewer can never take your app down. Adding the key
  yourself is still worthwhile on ProMotion devices — it unlocks 120 Hz.
- **Keep your shared module lean.** If you only capture HTTP (no custom debug
  UI), you do **not** need any Compose Multiplatform dependencies of your own —
  Sharingan brings its UI with it.
````

- [ ] **Step 3.4: README — add the pure-Swift XCFramework path**

Add a new subsection after the KMP setup section:

````markdown
### Pure Swift app (no Kotlin, XCFramework)

```bash
./gradlew :sharingan:assembleSharinganReleaseXCFramework        # debug-tool build
./gradlew :sharingan-noop:assembleSharinganReleaseXCFramework   # inert twin
```

Outputs land in `sharingan/build/XCFrameworks/release/Sharingan.xcframework`
and `sharingan-noop/build/XCFrameworks/release/Sharingan.xcframework` — same
framework name on purpose, so `import Sharingan` compiles in every
configuration and your build settings decide which one links:

1. Copy both, e.g. `Vendor/Debug/Sharingan.xcframework` and
   `Vendor/Release/Sharingan.xcframework`.
2. Add one of them to the target (General → Frameworks → Embed & Sign), then
   make both the search path **and the embed input** configuration-dependent:
   set `FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/Vendor/Debug` for Debug and
   `…/Release` for Release, and reference the framework in the Embed
   Frameworks phase via `$(FRAMEWORK_SEARCH_PATHS)`. Linking one variant but
   embedding the other dyld-crashes at launch — verify with
   `codesign -dv` / `ls` on the built `.app` that the embedded framework
   matches the configuration.
3. `import Sharingan`, then `SharinganUI.presentSharingan(animated: true)`.

Don't mix this with the Maven/KMP path in one app — pick one.
````

- [ ] **Step 3.5: RECIPES.md — update the SwiftUI recipe**

In `docs/RECIPES.md`, find the SwiftUI wrapper recipe (search for `SharinganViewControllerKt`) and replace every occurrence of `SharinganViewControllerKt.SharinganViewController()` with `SharinganUI.SharinganViewController()`; add a line noting `SharinganUI.presentSharingan(animated:)` as the no-wrapper alternative.

- [ ] **Step 3.6: AGENTS.md + llms.txt — update the machine-readable API reference**

In both files, update the iOS API entries: the ObjC/Swift facade class is `SharinganUI`; functions `SharinganViewController(): UIViewController` and `presentSharingan(animated: Boolean = true)`; XCFramework build tasks as in Step 3.4; the `iosMain` `api` + `export()` requirement from Step 3.2. Keep the two files content-identical (llms.txt mirrors AGENTS.md).

- [ ] **Step 3.7: Site pages — mirror the same content**

Run `grep -rln "dev.sharingan" site/` and update each hit: the getting-started page gets the "Get the artifacts" + corrected KMP snippet + iOS section from Steps 3.1–3.3; the integrations page gets the XCFramework section from Step 3.4. Same code blocks, adapted to the page's HTML/markdown structure.

- [ ] **Step 3.8: Docs self-check**

Run: `grep -rn "SharinganViewControllerKt" README.md docs/RECIPES.md AGENTS.md llms.txt site/ | grep -v superpowers`
Expected: no hits (the broken name is gone everywhere).
Run: `grep -rln "publishToMavenLocal" README.md site/`
Expected: at least README.md and one site page.

- [ ] **Step 3.9: Commit**

```bash
git add README.md docs/RECIPES.md AGENTS.md llms.txt site/
git commit -m "Docs: iOS onboarding that actually compiles — mavenLocal, export(), Xcode wiring, XCFramework path

Every undocumented step the first-time-consumer field test hit is now
written down: artifact acquisition, iosMain api + export() requirement,
the -Prelease build-phase hazard, pinned version matrix, pure-Swift
XCFramework embedding, and the real SharinganUI Swift names.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Chore — Compose Gradle DSL deprecation cleanup

**Deliverable:** After this commit, a consumer copying our build script pattern no longer inherits deprecation warnings.

**Files:**
- Modify: `sharingan/build.gradle.kts:36-40` (the five `compose.*` accessors)

- [ ] **Step 4.1: Capture the before-state of dependency resolution**

Run: `./gradlew :sharingan:dependencies --configuration iosSimulatorArm64CompileKlibraries -q > /tmp/deps-before.txt && grep -c "org.jetbrains.compose" /tmp/deps-before.txt`
Expected: a count > 0; file saved for the after-diff.

- [ ] **Step 4.2: Identify the exact deprecation and replace**

Run: `./gradlew :sharingan:help 2>&1 | grep -i deprecat | head -5` then build once with `--warning-mode all` to capture the accessor deprecation text: `./gradlew :sharingan:compileKotlinIosSimulatorArm64 --warning-mode all 2>&1 | grep -A2 "is deprecated" | head -20`.
Replace the deprecated accessors in `sharingan/build.gradle.kts` commonMain dependencies with the replacement named by the warning itself (the CMP plugin names its successor accessor in the message). If — and only if — the warning names no successor, use explicit coordinates pinned to the CMP version from the version catalog:

```kotlin
            val cmp = libs.versions.composeMultiplatform.get()
            implementation("org.jetbrains.compose.runtime:runtime:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.material3:material3:$cmp")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.components:components-ui-tooling-preview:$cmp")
```

(Check the version-catalog key with `grep -n "composeMultiplatform\|compose-multiplatform" gradle/libs.versions.toml` and use the actual accessor name.)

- [ ] **Step 4.3: Verify resolution is unchanged**

Run: `./gradlew :sharingan:dependencies --configuration iosSimulatorArm64CompileKlibraries -q > /tmp/deps-after.txt && diff /tmp/deps-before.txt /tmp/deps-after.txt && echo "RESOLUTION UNCHANGED"`
Expected: `RESOLUTION UNCHANGED` (empty diff). A non-empty diff is a STOP: revert and re-read the warning text — the replacement must be resolution-neutral.

- [ ] **Step 4.4: Full build + tests still green**

Run: `./gradlew :sharingan:testDebugUnitTest :sharingan:compileKotlinIosSimulatorArm64 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.5: Commit**

```bash
git add sharingan/build.gradle.kts
git commit -m "Chore: replace deprecated Compose DSL accessors (resolution-neutral, diff-verified)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Delegated field re-verification (supervised)

**Deliverable:** Independent evidence that the documented iOS journey now works: docs-only KMP integration, visually rendering viewer, zero crash reports, working pure-Swift XCFramework app.

This task is executed by delegating to the OpenCode #2 agent via `maestri ask` (it owns the consumer projects), with the supervising agent inspecting all screenshots personally. Not a code task — no TDD steps.

- [ ] **Step 5.1: Re-publish and brief the field agent**

Run `./gradlew publishToMavenLocal` in the library repo first. Then `maestri ask "OpenCode #2"` with: (1) delete the hand-rolled wrapper `shared/src/iosMain/.../SharinganIos.kt` and the `api`-placement workarounds in `~/sharingan-ios-consumer-test`; (2) re-integrate strictly per the updated README (it now has the `export()` recipe and `SharinganUI` names); (3) rebuild, run on the booted iPhone 17 Pro simulator, trigger traffic, present the viewer via `SharinganUI.presentSharingan(animated: true)`; (4) screenshot every state to `~/sharingan-ios-consumer-test/screenshots-v2/` including a landscape rotation (`xcrun simctl` cannot rotate — ask it to use AppleScript `System Events` keystrokes on the Simulator app, Cmd+Right Arrow, or flag it for manual check); (5) list any step it needed that the docs don't state.

- [ ] **Step 5.2: Supervisor inspects the evidence**

Read every screenshot in `screenshots-v2/` directly (the field agent cannot see images — mislabeled screenshots happened in round 1). Gates: viewer event list visibly rendered (3 tabs, event rows); event detail reachable; no springboard-only frames; `ls ~/Library/Logs/DiagnosticReports/ | grep SharinganConsumer | grep <today>` shows no new `.ips` files after the run.

- [ ] **Step 5.3: Pure-Swift XCFramework field test**

`maestri ask "OpenCode #2"`: build both XCFrameworks per README, create a fresh pure-Swift app (`~/sharingan-swift-consumer-test`, no Kotlin/Gradle), follow only the README "Pure Swift app" section: embed, configuration-dependent search path + embed phase, URLSession or manual `Sharingan` capture call per docs, present viewer, screenshot, build the Release configuration too and confirm it links the noop (viewer presents as an empty controller).

- [ ] **Step 5.4: Adjudicate and fix**

Any documented-step failure or rendering issue becomes a fix on the relevant Task 1–4 area, committed as `Fix from iOS field re-test: <issue>`. Re-run the failed leg of verification after each fix. Done when both legs pass with docs-only integration.

---

## Self-Review (completed at write time)

- **Spec coverage:** §1 → Task 1; §2 → Task 2; §3 → Task 3; §4 → Task 5; §5 commit plan → Tasks 1–5 map one-to-one (spec commit 5 = Task 5 outputs). Out-of-scope list untouched. ✓
- **Placeholders:** none — every code step has full code; Step 2.4/3.x name-pinning is an explicit measured value with a fallback instruction, not a TBD. ✓
- **Type consistency:** `topmostViewController(root: UIViewController): UIViewController` used identically in Task 1 test/impl/caller; `SharinganUI` facade name consistent across Tasks 1–3 (with the Step 2.4 pin gating Task 3). ✓
