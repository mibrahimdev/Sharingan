package dev.sharingan.ui

import dev.sharingan.SharinganEvent

/** The three protocol tabs, in design order. */
internal enum class Protocol(val label: String) {
    HTTP("HTTP"),
    MQTT("MQTT"),
    BLE("Bluetooth"),
}

/** One quick-filter chip below the search field. */
internal data class FilterChipSpec(val key: String, val label: String)

// Per-protocol knowledge (chips, matching, search haystacks) lives in the
// ProtocolDescriptors; these functions are the stable seam callers and
// tests go through.

internal fun protocolOf(event: SharinganEvent): Protocol = descriptorOf(event).protocol

/** The design's per-protocol chip rows. */
internal fun chipsFor(protocol: Protocol): List<FilterChipSpec> = descriptorOf(protocol).chips

internal fun matchesChip(event: SharinganEvent, chipKey: String): Boolean =
    chipKey == "all" || descriptorOf(event).matchesChip(event, chipKey)

internal fun matchesQuery(event: SharinganEvent, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return descriptorOf(event).haystack(event).any { it != null && q in it.lowercase() }
}

/** Events for one tab after chip + search filtering, oldest first. */
internal fun visibleEvents(
    events: List<SharinganEvent>,
    protocol: Protocol,
    chipKey: String,
    query: String,
): List<SharinganEvent> = events.filter {
    protocolOf(it) == protocol && matchesChip(it, chipKey) && matchesQuery(it, query)
}
