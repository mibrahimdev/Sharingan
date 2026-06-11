package dev.sharingan.internal

import dev.sharingan.SharinganEvent
import dev.sharingan.ui.Protocol
import dev.sharingan.ui.descriptorOf
import dev.sharingan.ui.protocolOf

/**
 * What the capture notification should say — the platform-free half of the
 * Android notification (and any future capture surface). Building it here
 * keeps every wording/state decision testable from common code; the
 * platform side only maps fields onto its framework builder.
 */
internal data class NotificationContent(
    /** Collapsed title, e.g. `Sharingan — Capturing · 12 events`. */
    val title: String,
    /** Collapsed line and first expanded line, e.g. `HTTP 5 · MQTT 4 · BLE 3`. */
    val countsLine: String,
    /** Expanded text: [countsLine] plus the last three events, newest first. */
    val expandedText: String,
    /** `Pause` while recording, `Resume` while paused. */
    val actionLabel: String,
)

/** Resolves the notification for the current state; null means nothing to post. */
internal fun notificationContentOf(
    events: List<SharinganEvent>,
    recording: Boolean,
): NotificationContent? {
    if (events.isEmpty()) return null
    val stateLabel = if (recording) "Capturing" else "Paused"
    val countsLine = Protocol.entries.joinToString(" · ") { protocol ->
        "${protocol.name} ${events.count { protocolOf(it) == protocol }}"
    }
    val ticker = events.takeLast(3).reversed().joinToString("\n") { descriptorOf(it).tickerLine(it) }
    return NotificationContent(
        title = "Sharingan — $stateLabel · ${events.size} events",
        countsLine = countsLine,
        expandedText = "$countsLine\n$ticker",
        actionLabel = if (recording) "Pause" else "Resume",
    )
}
