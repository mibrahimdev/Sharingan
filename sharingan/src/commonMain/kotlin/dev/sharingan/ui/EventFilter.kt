package dev.sharingan.ui

import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.shortLabel

/** The three protocol tabs, in design order. */
internal enum class Protocol(val label: String) {
    HTTP("HTTP"),
    MQTT("MQTT"),
    BLE("Bluetooth"),
}

internal fun protocolOf(event: SharinganEvent): Protocol = when (event) {
    is HttpEvent -> Protocol.HTTP
    is MqttEvent -> Protocol.MQTT
    is BleEvent -> Protocol.BLE
}

/** One quick-filter chip below the search field. */
internal data class FilterChipSpec(val key: String, val label: String)

/** The design's per-protocol chip rows. */
internal fun chipsFor(protocol: Protocol): List<FilterChipSpec> = when (protocol) {
    Protocol.HTTP -> listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("err", "Errors"),
        FilterChipSpec("2xx", "2xx"),
        FilterChipSpec("get", "GET"),
        FilterChipSpec("post", "POST"),
    )
    Protocol.MQTT -> listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("pub", "Pub"),
        FilterChipSpec("recv", "Recv"),
        FilterChipSpec("sub", "Sub"),
    )
    Protocol.BLE -> listOf(
        FilterChipSpec("all", "All"),
        FilterChipSpec("notify", "Notify"),
        FilterChipSpec("read", "Read"),
        FilterChipSpec("err", "Errors"),
    )
}

internal fun matchesChip(event: SharinganEvent, chipKey: String): Boolean {
    if (chipKey == "all") return true
    return when (event) {
        is HttpEvent -> when (chipKey) {
            "err" -> event.isFailure
            "2xx" -> (event.statusCode ?: 0) in 200..299
            "get" -> event.method.equals("GET", ignoreCase = true)
            "post" -> event.method.equals("POST", ignoreCase = true)
            else -> true
        }
        is MqttEvent -> when (chipKey) {
            "pub" -> event.direction == MqttDirection.PUBLISH
            "recv" -> event.direction == MqttDirection.RECEIVE
            "sub" -> event.direction == MqttDirection.SUBSCRIBE
            else -> true
        }
        is BleEvent -> when (chipKey) {
            "notify" -> event.operation == BleOperation.NOTIFY
            "read" -> event.operation == BleOperation.READ
            "err" -> event.isFailure
            else -> true
        }
    }
}

internal fun matchesQuery(event: SharinganEvent, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return haystack(event).any { it != null && q in it.lowercase() }
}

private fun haystack(event: SharinganEvent): List<String?> = when (event) {
    is HttpEvent -> buildList {
        add(event.method)
        add(event.url)
        add(event.statusCode?.toString())
        add(event.error)
        add(event.requestBody)
        add(event.responseBody)
        event.requestHeaders.forEach { add("${it.first}: ${it.second}") }
        event.responseHeaders.forEach { add("${it.first}: ${it.second}") }
    }
    is MqttEvent -> listOf(event.direction.shortLabel, event.topic, event.payload, event.error)
    is BleEvent -> listOf(
        event.operation.name, event.device, event.characteristic, event.uuid, event.payload, event.error,
    )
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
