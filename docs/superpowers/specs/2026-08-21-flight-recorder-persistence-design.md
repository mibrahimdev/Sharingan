# Flight-recorder persistence — design

**Issue:** [#27](https://github.com/mibrahimdev/Sharingan/issues/27) · **Date:** 2026-08-21 · **Status:** approved, ready to break into slices

Moves Sharingan from a live in-memory tail to a **flight recorder**: captured
events are persisted to a local on-device database so logs survive process
death, are grouped **per session**, and previous sessions can be browsed and
exported. The in-memory ring buffer (`SharinganStore`, capacity 300) stays the
live UI window; the DB augments it with deep, crash-surviving history.

## Decisions settled in the design session

| Question (#27) | Decision | Why |
|---|---|---|
| DB engine | **SQLDelight** | Repo has no KSP today; Room KMP forces KSP repo-wide **and** its bundled SQLite driver adds ~1 MB+/arch to the XCFramework. SQLDelight links system SQLite (tens of KB), confines to `:sharingan`, and its `transaction {}` maps onto the write-behind batch flush. |
| Session model | **Auto, one per process launch** | Hands-free. A session opens on the first event after launch and closes implicitly at process death. The instant a consumer must call `startSession()` they forget it before the crash that mattered. Also adds zero public surface. |
| Retrieval UX | **Full in-viewer history** | Past sessions render in the existing viewer exactly like the live one. Requires a decoder → drives the `kotlinx.serialization` dependency + `@Serializable` DTOs. |
| Retention | **Configurable `maxSessions`, device-clamped** | This is what the closed #18 capacity knob was deferred to become. Default 50, clamped to a storage-derived ceiling. |
| Disk security | **Bodies off disk by default** + inherited redaction + retention/purge | Debug-only mitigation without encryption weight. |
| Encryption | **Deferred to a future additive epic** | The one slice carrying real iOS Native risk (libsqlcipher linking) for value secondary to crash-survival. Slots in behind the same `configure()` surface later. |

## Non-negotiable constraints (from #27)

- **Noop parity is sacred.** All DB/serialization code lives **inside `:sharingan`** behind `internal` symbols. `:sharingan-noop` gains **no** SQLDelight, no kotlinx.serialization, no DB — it mirrors only the *public* symbols we add, as empty no-ops. `checkApiParity` (CI, #12) enforces this on every change.
- **Event ABI is frozen (#15).** We do **not** annotate the public `HttpEvent`/`MqttEvent`/`BleEvent` with `@Serializable` — that perturbs their ABI. A separate internal DTO layer mirrors them instead.
- **The `record()` hot path must not regress.** It stays a lock-free CAS append; persistence hangs off an internal seam that never blocks the caller.

## Architecture

New internal package `dev.sharingan.persistence`, none of it public:

- **`SharinganDb`** — SQLDelight-generated database + `SqlDriverFactory` (`expect`/`actual`: `AndroidSqliteDriver` / `NativeSqliteDriver`).
- **`PersistenceController`** — owns the write-behind machinery, session lifecycle, and retention. Wires into the store via the internal seam. Owns its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)` — the library has no shared commonMain scope today (the only existing one is Android-notification-owned).
- **`EventDto`** — `@Serializable` sealed DTO (three variants) with `toEvent()` / `fromEvent()` mappers. Stored as the JSON blob; decoded back into public events (via their existing internal constructors) for in-viewer history. This is the **decoder** the current hand-rolled `SharinganExport` encoder lacks.

## Write-behind hot path

`SharinganStore.record()` keeps its exact CAS append and gains one internal seam:

```
record(event):
    CAS-append to _events        // unchanged — the live 300-event window
    onRecord?.invoke(event)      // new: internal var, null unless persistence is on
```

`onRecord` is `internal` → invisible to the public API → **noop never sees it,
parity untouched**. `PersistenceController` sets it to `channel.trySend(event)`
(bounded `Channel`, non-blocking `trySend`, O(1)). A single flusher coroutine on
`Dispatchers.Default` drains the channel, batches by size-or-time (e.g. 50
events / 250 ms, whichever first), and writes each batch in one SQLDelight
`transaction {}`.

Tapping `record()` directly — not observing `store.events` — is deliberate: the
StateFlow is a ring buffer that evicts at 300, so under a burst you'd lose
events before a flusher could diff the list. The channel captures every event
regardless of eviction.

## Data model

Two SQLDelight tables:

- `session(id, started_at, app_id, build, os, device_model)` — one row per process launch, created lazily on the first event.
- `event(id, session_id FK, timestamp, type, is_failure, host_or_topic, payload_json)` — indexed columns for cheap listing/filtering + the `@Serializable` DTO JSON blob for full fidelity.

New dependencies, scoped to `:sharingan`: `sqldelight` (plugin + runtime +
`native-driver` + `android-driver`) and `kotlinx-serialization-json` (+ plugin).

## Public API surface (minimized)

Retrieval is driven by the **in-app viewer**, which is internal to `:sharingan`
and can freely use internal APIs — so **no public programmatic session API** is
needed this epic. That trims #27's point 8. The entire new public surface is one
config entry point:

```kotlin
Sharingan.configure(
    persistence: Boolean = true,     // hands-free: on by default in debug
    maxSessions: Int = 50,           // clamped to a device-derived ceiling
    persistBodies: Boolean = false,  // request/response bodies OFF disk by default
)
```

One method + defaults — the minimum noop tax. Every symbol here doubles into
`sharingan-noop` as a no-op, then `apiDump` + `checkApiParity` per
`docs/api-parity.md`.

## Security model (no encryption this epic)

- **Bodies off disk by default** — a *new* gate independent of the in-memory `captureBodies` (which stays `true`). With `persistBodies = false`, the DTO drops bodies/payloads before the blob is written; the live in-memory viewer still shows them, the disk file does not.
- **Header redaction inherited for free** — `HttpLogger` redacts before `record()`, so persisted events already carry `••••` for `Authorization`/`Cookie`/etc.
- **Clear-on-new-session** via retention; `Sharingan.clear()` also purges the current session's rows.
- **Honest caveat:** a plaintext debug DB is extractable on a compromised device. Encryption is a deferred additive epic (roadmap + follow-up issue), designed to slot in behind `configure()` later.

## Retention

`maxSessions` (default 50) clamped to a ceiling derived at runtime from
available storage. On each new session's creation, purge the oldest sessions
beyond the limit (cascade-deletes their events).

## Slices (deliverable-shaped, sequenced)

Each slice ends with a demoable, user-visible deliverable; unit/integration
tests land with each slice. E2E for the session-picker flow is deferred until
the viewer UI settles (bundled near slice 3 / end), per the E2E-timing rule.

1. **DB foundation** — SQLDelight + serialization deps, schema, `expect`/`actual` drivers, one round-trip test. *After this: the library opens an on-device DB and inserts/reads a row on Android + iOS.*
2. **Write-behind capture** — `onRecord` seam, channel, batched flusher, lazy session row. *After this: events from a run are written to disk and survive process death — the core flight-recorder value.*
3. **In-viewer history** — session-picker UI, DTO decode, render past sessions in the existing list/detail. *After this: user opens Sharingan, sees previous sessions, taps one, browses its logs.*
4. **Retention + `configure()`** — `maxSessions` clamp, purge-oldest, the public config entry point + noop mirror + `apiDump`/`checkApiParity`. *After this: old sessions auto-purge; consumer can tune the count (one public method lands).*
5. **Disk security** — bodies-off-by-default gate, clear-on-new-session. *After this: by default no request/response bodies hit disk; opt-in to include them.*

## Testing strategy

Per-slice: SQLDelight round-trip tests, DTO encode/decode round-trip
(`commonTest` — mind the Kotlin/Native no-commas-in-backtick-names rule),
flusher batching under burst, retention purge math. Given/When/Then naming.
E2E for the session-picker flow lands once slice 3's UI is settled, with the
`HiltTestApplication`-style harness bundled in — not per-slice.

## Risks

- **iOS klib/build surface** — adding `NativeSqliteDriver` actuals touches the same area as open bug #41 (transient klib `AssertionError` on the noop iOS klib). Watch during slice 1.
- **ABI discipline** — DTOs kept separate precisely so the #15-frozen event ABI is never touched.
- **Persistence defaults on in debug** — intended (hands-free), but means every debug build starts writing a DB; documented so it isn't a surprise.

## Deviations from #27 (approved)

- No public programmatic session API — retrieval is in-viewer only, trimming point 8 to shrink the noop tax.
- Persistence defaults **on** in debug; bodies default **off** on disk.
- Encryption deferred to a future additive epic rather than shipped in this one.
