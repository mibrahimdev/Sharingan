package dev.sharingan

import dev.sharingan.internal.formatClockTime
import dev.sharingan.ui.descriptorOf

/**
 * Turns captured events into shareable text.
 *
 * These are the exact formatters behind the in-app share sheet, exposed as
 * public API so scripts, tests and AI agents can produce identical output:
 *
 * - [agentMarkdown] — structured Markdown that LLMs parse reliably; the
 *   "Copy for AI agent" action.
 * - [curl] — a reproducible `curl` command for an HTTP event.
 * - [json] / [sessionJson] — machine-readable single-event / full-session JSON.
 * - [summary] — a compact human-readable digest, one line per event.
 *
 * Per-event fragments live on each protocol's descriptor; this object owns
 * the public surface and the session-level assembly (count headers,
 * separators, tool metadata).
 */
public object SharinganExport {

    /** Structured Markdown for one event — the "Copy for AI agent" format. */
    public fun agentMarkdown(event: SharinganEvent): String =
        descriptorOf(event).agentMarkdown(event)

    /** Structured Markdown for a whole session: a count header, then one section per event. */
    public fun agentMarkdown(events: List<SharinganEvent>): String = buildString {
        appendLine("# Sharingan session export")
        appendLine(countsLine(events))
        for (event in events) {
            appendLine()
            appendLine("---")
            appendLine()
            append(agentMarkdown(event))
            appendLine()
        }
    }.trimEnd()

    /** A reproducible `curl` command for [event]. Redacted headers stay masked. */
    public fun curl(event: HttpEvent): String = buildString {
        append("curl -X ").append(event.method)
        append(" ").append(shellQuote(event.url))
        for ((name, value) in event.requestHeaders) {
            append(" \\\n  -H ").append(shellQuote("$name: $value"))
        }
        event.requestBody?.let { body ->
            append(" \\\n  --data ").append(shellQuote(body))
        }
    }

    /** Machine-readable JSON for one event. */
    public fun json(event: SharinganEvent): String = eventJson(event, indent = "")

    /** Machine-readable JSON for a whole session, wrapped with tool metadata. */
    public fun sessionJson(events: List<SharinganEvent>): String = buildString {
        appendLine("{")
        appendLine("  \"tool\": \"sharingan\",")
        appendLine("  \"eventCount\": ${events.size},")
        appendLine("  \"events\": [")
        events.forEachIndexed { index, event ->
            append(eventJson(event, indent = "    "))
            appendLine(if (index < events.lastIndex) "," else "")
        }
        appendLine("  ]")
        append("}")
    }

    /** Human-readable digest: a count header, then one line per event. */
    public fun summary(events: List<SharinganEvent>): String = buildString {
        appendLine("Sharingan session · ${countsLine(events)}")
        for (event in events) {
            append(formatClockTime(event.timestampMillis)).append("  ")
            appendLine(descriptorOf(event).summaryLine(event))
        }
    }.trimEnd()

    // ── session assembly ─────────────────────────────────────────

    private fun countsLine(events: List<SharinganEvent>): String {
        val http = events.count { it is HttpEvent }
        val mqtt = events.count { it is MqttEvent }
        val ble = events.count { it is BleEvent }
        return "${events.size} events · HTTP $http · MQTT $mqtt · BLE $ble"
    }

    private fun eventJson(event: SharinganEvent, indent: String): String {
        val fields = mutableListOf<Pair<String, String>>()
        fields += "id" to "\"${jsonEscape(event.id)}\""
        fields += "timestampMillis" to event.timestampMillis.toString()
        fields += descriptorOf(event).jsonFields(event)
        event.error?.let { fields += "error" to "\"${jsonEscape(it)}\"" }

        return fields.joinToString(
            separator = ",\n",
            prefix = "$indent{\n",
            postfix = "\n$indent}",
        ) { (name, raw) -> "$indent  \"$name\": $raw" }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
