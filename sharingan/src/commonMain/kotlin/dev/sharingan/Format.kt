package dev.sharingan

import kotlin.math.roundToLong

/**
 * Formats a byte count the way the design's rows render sizes:
 * `0 B`, `312 B`, `4.2 KB`, `1.1 MB` — `—` when unknown.
 */
internal fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${oneDecimal(kb)} KB"
    return "${oneDecimal(kb / 1024.0)} MB"
}

private fun oneDecimal(value: Double): String {
    val scaled = (value * 10).roundToLong()
    return "${scaled / 10}.${scaled % 10}"
}

/** `PUB` / `RECV` / `SUB` — the compact labels used in rows and exports. */
internal val MqttDirection.shortLabel: String
    get() = when (this) {
        MqttDirection.PUBLISH -> "PUB"
        MqttDirection.RECEIVE -> "RECV"
        MqttDirection.SUBSCRIBE -> "SUB"
    }

/** Escapes a string for embedding inside a JSON string literal. */
internal fun jsonEscape(value: String): String = buildString(value.length + 8) {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else ->
                if (ch < ' ') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
        }
    }
}
