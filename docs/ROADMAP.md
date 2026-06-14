# Roadmap

Future enhancements, recorded but not scheduled. Items here are deliberately
out of scope for current work; pick one up when its motivating use case becomes
a priority.

## Storage & persistence — "flight recorder" (v2 epic)

**Status: needs a design session before any code — see [#27](https://github.com/mibrahimdev/Sharingan/issues/27).**

Today `SharinganStore` is an in-memory ring buffer (capacity 300); logs are lost
on process death. The proposal is to persist captured events to a local on-device
database so logs **survive crashes**, are grouped **per session**, and a consumer
can **dump previous sessions' logs anytime** (flight-recorder model).

Why it's worth doing: the logs you most want are the ones right before a crash —
exactly what an in-memory buffer loses. Per-session history also enables a "send
me the logs from your last run" QA workflow.

Design constraints to settle in the session (full list in #27):

- **Noop parity is sacred.** Persistence must live *entirely behind* the existing
  `SharinganStore` surface (`record()` / `events: StateFlow` / `clear()`).
  `:sharingan-noop` stays empty/in-memory with a byte-identical public API and
  **zero DB dependency** — it ships in release builds. No DB type may leak into
  the public API.
- **DB engine — decide:** SQLDelight (KMP-first, lean, mature on Native — current
  lean) vs Room KMP (2.7+, Android-familiar, heavier on iOS).
- **Protect the hot path.** `record()` is a lock-free CAS on an in-memory list;
  don't write to disk per event. Use a **write-behind buffer** — the in-memory
  `StateFlow` stays the live UI window, batches flush to the DB on a background
  dispatcher. The DB augments the ring buffer; it doesn't replace it.
- **Schema:** session table (id, started-at, app/build/device meta) + events table
  (FK session, timestamp, type, indexed cols + `kotlinx.serialization` JSON blob).
  Composes with the event ABI frozen in #15.
- **Retention policy** (max sessions / age / rows-per-session) is what
  `configure(capacity)` (closed #18) becomes — added additively once persistence
  exists, which is why the simple in-memory capacity knob was dropped.
- **Security must be designed in, not bolted on.** A SQLite file is extractable;
  persisting request/response bodies can write tokens/PII to disk. Need
  bodies-off-by-default for persistence, redaction, retention/auto-purge,
  clear-on-new-session. Debug-only mitigates but isn't sufficient alone.

Sequencing: ship 1.0 on the in-memory architecture first (close the parity check
#12), then take persistence on as a deliberate v2 epic.

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
