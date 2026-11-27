# Sharingan

Domain vocabulary for Sharingan. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for system design.

## Terms

**Event**: One captured protocol interaction, such as an HTTP exchange, MQTT message, or BLE operation. An event is immutable once recorded.

**Protocol**: One of the traffic types Sharingan captures: HTTP, MQTT, or BLE. Each **Protocol** has its own view in the browser.

**Protocol Descriptor**: The description of how a **Protocol** is presented and explored, including its filters, search, rows, details, and exports.

**Capture**: The act of recording a protocol interaction as an **Event**. Capture follows the active recording state.

**Flight Recorder**: The persistent history of events from process lifetimes. The **Store** remains the live source of truth for the **Current Session**; the Flight Recorder is queried on demand to list or inspect **Past Sessions**.

**Logger**: A protocol-specific interface for recording an **Event** from a host app's network or device client.

**Session**: A collection of events associated with one process lifetime. The **Current Session** is active; previous lifetimes are **Past Sessions**.

**Current Session**: The live process lifetime backed by the **Store**.

**Past Session**: A previous process lifetime retrieved on demand from the **Flight Recorder**.

**Store**: The live collection of captured **Events** for the **Current Session**. It is the source of truth while the process is running.

**Write-Behind Seam**: The process that mirrors accepted **Events** to the **Flight Recorder** without blocking **Capture**.

**Capture Notification**: The Android notification that surfaces capture status and recent **Events**.

**Ticker**: A short preview of recent **Events** shown in the expanded **Capture Notification**.

**Agent Markdown**: The structured Markdown export intended to help an agent understand and debug captured **Events**.

**Share Action / Share Scope**: A Share Action chooses how data is shared; a Share Scope chooses whether it covers one **Event** or all **Events** in the current **Protocol**.

**No-op Twin**: The inert release counterpart of Sharingan, preserving its public API without capturing events or displaying the browser.
