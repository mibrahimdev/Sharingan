package dev.sharingan

import dev.sharingan.internal.formatClockTime

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
 */
public object SharinganExport {

    /** Structured Markdown for one event — the "Copy for AI agent" format. */
    public fun agentMarkdown(event: SharinganEvent): String = when (event) {
        is HttpEvent -> httpMarkdown(event)
        is MqttEvent -> mqttMarkdown(event)
        is BleEvent -> bleMarkdown(event)
    }

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
            appendLine(summaryLine(event))
        }
    }.trimEnd()

    // ── markdown per protocol ────────────────────────────────────

    private fun httpMarkdown(e: HttpEvent): String = buildString {
        appendLine("## ${e.method} ${e.path}")
        val status = e.statusCode?.toString() ?: "—"
        val duration = e.durationMillis?.let { " · **${it}ms**" } ?: ""
        val size = e.responseSizeBytes?.let { " · ${formatBytes(it)}" } ?: ""
        appendLine("**Status:** $status$duration$size")
        appendLine("**Host:** ${e.host}")
        e.error?.let { appendLine("**Error:** $it") }
        if (e.requestHeaders.isNotEmpty()) {
            appendLine()
            appendLine("### Request headers")
            e.requestHeaders.forEach { (k, v) -> appendLine("- $k: $v") }
        }
        if (e.responseHeaders.isNotEmpty()) {
            appendLine()
            appendLine("### Response headers")
            e.responseHeaders.forEach { (k, v) -> appendLine("- $k: $v") }
        }
        e.requestBody?.let { appendBodySection("Request body", it) }
        e.responseBody?.let { appendBodySection("Response body", it) }
    }.trimEnd()

    private fun mqttMarkdown(e: MqttEvent): String = buildString {
        appendLine("## MQTT ${e.direction.shortLabel} ${e.topic}")
        appendLine("**QoS:** ${e.qos} · **Retained:** ${e.retained} · ${formatBytes(e.payloadSizeBytes)}")
        e.error?.let { appendLine("**Error:** $it") }
        e.payload?.let { appendBodySection("Payload", it) }
    }.trimEnd()

    private fun bleMarkdown(e: BleEvent): String = buildString {
        appendLine("## BLE ${e.operation.name} ${e.characteristic ?: e.device}")
        appendLine("**Device:** ${e.device} · **UUID:** ${e.uuid ?: "—"} · ${formatBytes(e.sizeBytes)}")
        e.error?.let { appendLine("**Error:** $it") }
        e.payload?.let { appendBodySection("Decoded value", it) }
    }.trimEnd()

    private fun StringBuilder.appendBodySection(title: String, body: String) {
        val looksJson = body.trimStart().firstOrNull() in setOf('{', '[')
        appendLine()
        appendLine("### $title")
        appendLine(if (looksJson) "```json" else "```")
        appendLine(body)
        appendLine("```")
    }

    // ── summaries ────────────────────────────────────────────────

    private fun countsLine(events: List<SharinganEvent>): String {
        val http = events.count { it is HttpEvent }
        val mqtt = events.count { it is MqttEvent }
        val ble = events.count { it is BleEvent }
        return "${events.size} events · HTTP $http · MQTT $mqtt · BLE $ble"
    }

    private fun summaryLine(event: SharinganEvent): String = when (event) {
        is HttpEvent -> {
            val status = event.statusCode?.toString() ?: (event.error ?: "—")
            val duration = event.durationMillis?.let { " (${it}ms)" } ?: ""
            "${event.method} ${event.path} → $status$duration"
        }
        is MqttEvent ->
            "${event.direction.shortLabel} ${event.topic} QoS${event.qos} ${formatBytes(event.payloadSizeBytes)}"
        is BleEvent ->
            "${event.operation.name} ${event.characteristic ?: "—"} (${event.device})"
    }

    // ── JSON ─────────────────────────────────────────────────────

    private fun eventJson(event: SharinganEvent, indent: String): String {
        val fields = mutableListOf<Pair<String, String>>()
        fun put(name: String, raw: String?) {
            raw?.let { fields += name to it }
        }
        fun putString(name: String, value: String?) {
            value?.let { fields += name to "\"${jsonEscape(it)}\"" }
        }

        putString("id", event.id)
        put("timestampMillis", event.timestampMillis.toString())
        when (event) {
            is HttpEvent -> {
                putString("protocol", "http")
                putString("method", event.method)
                putString("url", event.url)
                put("statusCode", event.statusCode?.toString())
                put("durationMillis", event.durationMillis?.toString())
                put("requestHeaders", headersJson(event.requestHeaders))
                put("responseHeaders", headersJson(event.responseHeaders))
                putString("requestBody", event.requestBody)
                putString("responseBody", event.responseBody)
                putString("contentType", event.contentType)
                put("responseSizeBytes", event.responseSizeBytes?.toString())
                put(
                    "timing",
                    event.timing.takeIf { it.isNotEmpty() }?.joinToString(
                        prefix = "[",
                        postfix = "]",
                    ) { "{\"label\": \"${jsonEscape(it.label)}\", \"millis\": ${it.millis}}" },
                )
            }
            is MqttEvent -> {
                putString("protocol", "mqtt")
                putString("direction", event.direction.shortLabel)
                putString("topic", event.topic)
                put("qos", event.qos.toString())
                put("retained", event.retained.toString())
                putString("payload", event.payload)
                put("payloadSizeBytes", event.payloadSizeBytes?.toString())
            }
            is BleEvent -> {
                putString("protocol", "ble")
                putString("operation", event.operation.name)
                putString("device", event.device)
                putString("characteristic", event.characteristic)
                putString("uuid", event.uuid)
                putString("payload", event.payload)
                put("sizeBytes", event.sizeBytes?.toString())
            }
        }
        putString("error", event.error)

        return fields.joinToString(
            separator = ",\n",
            prefix = "$indent{\n",
            postfix = "\n$indent}",
        ) { (name, raw) -> "$indent  \"$name\": $raw" }
    }

    private fun headersJson(headers: List<Pair<String, String>>): String? {
        if (headers.isEmpty()) return null
        return headers.joinToString(prefix = "[", postfix = "]") { (k, v) ->
            "{\"name\": \"${jsonEscape(k)}\", \"value\": \"${jsonEscape(v)}\"}"
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
