# Sharingan

The domain language of Sharingan, an on-device multiprotocol debug logger for Kotlin Multiplatform.
This file names the concepts; `docs/ARCHITECTURE.md` explains how they're built.

## Language

**Event**:
One captured protocol interaction — an HTTP exchange, an MQTT message, or a BLE operation. Immutable once recorded; `SharinganEvent` is the sealed root.
_Avoid_: log, entry, record (as a noun), request (HTTP-only)

**Protocol**:
One of the three traffic kinds Sharingan understands — HTTP, MQTT, BLE. Each Protocol is a tab in the browser and a subtype of Event.
_Avoid_: transport, channel, tap

**Protocol Descriptor**:
The single home for everything Sharingan knows about one Protocol: its filter chips, chip matching, search haystack, row presentation, notification ticker line, per-event export fragments, and detail body. Exactly one exhaustive `when (event)` maps an Event to its Descriptor; adding a Protocol means writing one Descriptor and registering it there.
_Avoid_: handler, strategy, adapter (reserved for things satisfying an interface at a seam)

**Capture**:
The act of recording an Event into the Store, via a Logger or the Ktor plugin. Header redaction happens at capture — secrets never enter the Store.
_Avoid_: logging, tracking, interception

**Logger**:
A per-Protocol capture entry point (`Sharingan.http`, `Sharingan.mqtt`, `Sharingan.ble`). Client-agnostic: host apps wire their own MQTT/BLE libraries into it.
_Avoid_: tracker, recorder

**Store**:
The in-memory ring buffer of Events (default 300). Memory-only by design — nothing is ever written to disk; process death clears it.
_Avoid_: database, cache, repository, buffer (alone)

**Capture Notification**:
The Android sticky notification showing per-Protocol counters, a three-event Ticker when expanded, and Pause/Resume. Silent, updated in place, and must never crash the host app.
_Avoid_: status bar entry, foreground notification (it isn't a foreground service)

**Ticker**:
The last-three-events preview inside the expanded Capture Notification, one line per Event.

**Agent Markdown**:
The structured Markdown export behind "Copy for AI agent" — the share sheet's primary action and Sharingan's reason for existing. Optimized for LLM parsing.
_Avoid_: AI export, markdown dump

**Share Action / Share Scope**:
A Share Action is what the share sheet does (copy for agent, copy human-readable, copy raw JSON, system share); a Share Scope is what it covers (the selected Event, or all Events in the current Protocol tab).

**No-op Twin**:
The `sharingan-noop` artifact: identical public interface to `sharingan`, inert implementations, real Event models. Release builds swap it in so Sharingan has zero release footprint. Every public declaration added to `sharingan` must be mirrored here.
_Avoid_: stub, mock, fake (those are test doubles; the Twin ships to production)

## Example dialogue

> **Dev:** A user says the MQTT tab shows "fail" on a publish that succeeded.
> **Maintainer:** That's row presentation, so look at the MQTT **Protocol Descriptor** — presentation, chips, and the **Ticker** line for MQTT all live there. If the **Event** itself has a wrong `error`, that's **Capture** — check the call into the MQTT **Logger**, not the UI.
> **Dev:** And if I fix the Descriptor, do I touch the **No-op Twin**?
> **Maintainer:** No — Descriptors are internal. The Twin only mirrors public declarations, like `SharinganExport` or the Loggers' signatures.
