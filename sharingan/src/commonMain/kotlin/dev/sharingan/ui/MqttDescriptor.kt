package dev.sharingan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.formatBytes
import dev.sharingan.internal.formatClockTime
import dev.sharingan.shortLabel

/** Everything Sharingan knows about MQTT events. */
internal object MqttDescriptor : ProtocolDescriptor<MqttEvent>() {

    override val protocol: Protocol = Protocol.MQTT
    override val eventNoun: String = "messages"
    override val tabIcon: ImageVector = SharinganIcons.Waves
    override val searchPlaceholder: String = "Filter topic, payload…"

    override val chips: List<FilterChipSpec> = listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("pub", "Pub"),
        FilterChipSpec("recv", "Recv"),
        FilterChipSpec("sub", "Sub"),
    )

    override fun chipMatches(event: MqttEvent, chipKey: String): Boolean = when (chipKey) {
        "pub" -> event.direction == MqttDirection.PUBLISH
        "recv" -> event.direction == MqttDirection.RECEIVE
        "sub" -> event.direction == MqttDirection.SUBSCRIBE
        else -> true
    }

    override fun searchHaystack(event: MqttEvent): List<String?> =
        listOf(event.direction.shortLabel, event.topic, event.payload, event.error)

    override fun present(colors: SharinganColors, event: MqttEvent): EventPresentation {
        val dirTint = colors.mqttDirectionTint(event.direction)
        return EventPresentation(
            lead = event.direction.shortLabel,
            leadTint = dirTint,
            main = event.topic,
            sub = if (event.retained) "retained" else null,
            status = "Q${event.qos}",
            statusTint = Tint(colors.textDim, colors.faint),
            right = if (event.isFailure) "fail" else "ok",
            rightColor = if (event.isFailure) colors.err else colors.textDim,
            sizeLabel = formatBytes(event.payloadSizeBytes),
            clockTime = formatClockTime(event.timestampMillis),
            railColor = if (event.isFailure) colors.err else dirTint.color,
            isFailure = event.isFailure,
        )
    }

    override fun ticker(event: MqttEvent): String = "${event.direction.shortLabel} ${event.topic}"

    override fun markdown(event: MqttEvent): String = buildString {
        appendLine("## MQTT ${event.direction.shortLabel} ${event.topic}")
        appendLine("**QoS:** ${event.qos} · **Retained:** ${event.retained} · ${formatBytes(event.payloadSizeBytes)}")
        event.error?.let { appendLine("**Error:** $it") }
        event.payload?.let { appendBodySection("Payload", it) }
    }.trimEnd()

    override fun summary(event: MqttEvent): String =
        "${event.direction.shortLabel} ${event.topic} QoS${event.qos} ${formatBytes(event.payloadSizeBytes)}"

    override fun fields(event: MqttEvent): List<Pair<String, String>> = buildList {
        putString("protocol", "mqtt")
        putString("direction", event.direction.shortLabel)
        putString("topic", event.topic)
        put("qos", event.qos.toString())
        put("retained", event.retained.toString())
        putString("payload", event.payload)
        put("payloadSizeBytes", event.payloadSizeBytes?.toString())
    }

    @Composable
    override fun Body(event: MqttEvent) {
        val colors = LocalSharinganColors.current
        val dirTint = colors.mqttDirectionTint(event.direction)
        Section("Message") {
            KeyValueRow("Direction", event.direction.shortLabel, dirTint.color)
            KeyValueRow("Topic", event.topic)
            KeyValueRow("QoS", event.qos.toString())
            KeyValueRow("Retained", event.retained.toString())
            KeyValueRow("Payload size", formatBytes(event.payloadSizeBytes))
            KeyValueRow(
                "Result",
                if (event.isFailure) "fail" else "ok",
                if (event.isFailure) colors.err else colors.ok,
            )
        }
        event.error?.let { ErrorSection(it) }
        event.payload?.takeIf { it.isNotBlank() }?.let {
            Section("Payload") { BodyBlock(it) }
        }
    }
}
