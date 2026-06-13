# Roadmap

Future enhancements, recorded but not scheduled. Items here are deliberately
out of scope for current work; pick one up when its motivating use case becomes
a priority.

## iOS / Swift ergonomics

- **iOS-friendly Swift `log()` overload.** The pure-Swift capture path
  (`Sharingan.shared.http.log(...)`) works but is clunky in Swift: Kotlin
  default arguments don't bridge, so every parameter is required, and the
  numeric params surface as boxed types (`SharinganInt?`, `SharinganLong?`)
  with a non-optional `timing: []`. Add an iOS-targeted overload (e.g. in
  `iosMain`) taking plain Swift-friendly types and sensible defaults so a Swift
  caller can log a request with just `method` and `url`. Removes the last rough
  edge documented in [RECIPES.md](RECIPES.md#ios--manual-http-logging-from-swift-urlsession-no-ktor).
- **Swift runtime bridge.** Expose `events`, `isRecording`, and `clear` to Swift
  so pure-Swift apps can drive the recorder programmatically, not just present
  the viewer.
- **SwiftUI `.sharinganSheet()` modifier / `UIApplication` extension sugar.**
  Suggested in field-test feedback; `presentSharingan()` covers the need for
  now.
- **In-viewer navigation hooks for UI automation.** Field testing could not
  reach the detail screen programmatically; revisit alongside E2E tooling.

## Distribution

- **SPM package / GitHub Releases hosting** of the XCFramework, to replace the
  manual `assembleSharinganReleaseXCFramework` + drag-and-drop step.
- **Maven Central publishing**, to replace the `mavenLocal()` install step.

## Android

- **Quickstart template / `sharingan init`** to scaffold a new project (both the
  iOS and Android field tests flagged hand-scaffolding as the biggest remaining
  friction).
- **Notification-permission doc polish** and **`dev.sharingan.show` import
  discoverability** improvements raised in the Android field test.
