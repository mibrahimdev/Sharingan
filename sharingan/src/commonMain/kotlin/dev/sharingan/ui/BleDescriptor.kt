package dev.sharingan.ui

import androidx.compose.runtime.Composable
import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.formatBytes
import dev.sharingan.internal.formatClockTime

/** Everything Sharingan knows about BLE events. */
internal object BleDescriptor : ProtocolDescriptor<BleEvent>() {

    override val protocol: Protocol = Protocol.BLE
    override val eventNoun: String = "operations"

    override val chips: List<FilterChipSpec> = listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("notify", "Notify"),
        FilterChipSpec("read", "Read"),
        FilterChipSpec("err", "Errors"),
    )

    override fun chipMatches(event: BleEvent, chipKey: String): Boolean = when (chipKey) {
        "notify" -> event.operation == BleOperation.NOTIFY
        "read" -> event.operation == BleOperation.READ
        "err" -> event.isFailure
        else -> true
    }

    override fun searchHaystack(event: BleEvent): List<String?> = listOf(
        event.operation.name, event.device, event.characteristic, event.uuid, event.payload, event.error,
    )

    override fun present(colors: SharinganColors, event: BleEvent): EventPresentation {
        val opTint = colors.bleOperationTint(event.operation)
        return EventPresentation(
            lead = event.operation.name,
            leadTint = opTint,
            main = event.characteristic ?: event.device,
            sub = event.uuid ?: event.device,
            status = event.uuid,
            statusTint = Tint(colors.textDim, colors.faint),
            right = if (event.isFailure) "err" else formatBytes(event.sizeBytes),
            rightColor = if (event.isFailure) colors.err else colors.textDim,
            sizeLabel = formatBytes(event.sizeBytes),
            clockTime = formatClockTime(event.timestampMillis),
            railColor = opTint.color,
            isFailure = event.isFailure,
        )
    }

    override fun ticker(event: BleEvent): String =
        "${event.operation.name} ${event.characteristic ?: event.device}"

    override fun markdown(event: BleEvent): String = buildString {
        appendLine("## BLE ${event.operation.name} ${event.characteristic ?: event.device}")
        appendLine("**Device:** ${event.device} · **UUID:** ${event.uuid ?: "—"} · ${formatBytes(event.sizeBytes)}")
        event.error?.let { appendLine("**Error:** $it") }
        event.payload?.let { appendBodySection("Decoded value", it) }
    }.trimEnd()

    override fun summary(event: BleEvent): String =
        "${event.operation.name} ${event.characteristic ?: "—"} (${event.device})"

    override fun fields(event: BleEvent): List<Pair<String, String>> = buildList {
        putString("protocol", "ble")
        putString("operation", event.operation.name)
        putString("device", event.device)
        putString("characteristic", event.characteristic)
        putString("uuid", event.uuid)
        putString("payload", event.payload)
        put("sizeBytes", event.sizeBytes?.toString())
    }

    @Composable
    override fun Body(event: BleEvent) {
        val colors = LocalSharinganColors.current
        Section("Operation") {
            KeyValueRow("Type", event.operation.name, colors.bleOperationTint(event.operation).color)
            KeyValueRow("Device", event.device)
            event.characteristic?.let { KeyValueRow("Characteristic", it) }
            event.uuid?.let { KeyValueRow("UUID", it) }
            KeyValueRow("Size", formatBytes(event.sizeBytes))
            KeyValueRow(
                "Result",
                if (event.isFailure) "fail" else "ok",
                if (event.isFailure) colors.err else colors.ok,
            )
        }
        event.error?.let { ErrorSection(it) }
        event.payload?.takeIf { it.isNotBlank() }?.let {
            Section("Decoded value") { BodyBlock(it) }
        }
    }
}
