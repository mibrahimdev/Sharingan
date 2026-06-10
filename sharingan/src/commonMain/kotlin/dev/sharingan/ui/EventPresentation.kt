package dev.sharingan.ui

import androidx.compose.ui.graphics.Color
import dev.sharingan.BleEvent
import dev.sharingan.BleOperation
import dev.sharingan.HttpEvent
import dev.sharingan.MqttDirection
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import dev.sharingan.formatBytes
import dev.sharingan.internal.formatClockTime
import dev.sharingan.shortLabel

/** A color plus its soft (translucent) background companion. */
internal data class Tint(val color: Color, val soft: Color)

/** Everything a row or detail title needs, resolved once per event. */
internal data class EventPresentation(
    val lead: String,
    val leadTint: Tint,
    val main: String,
    val sub: String?,
    val status: String?,
    val statusTint: Tint,
    val right: String?,
    val rightColor: Color?,
    val sizeLabel: String,
    val clockTime: String,
    val railColor: Color,
    val isFailure: Boolean,
)

internal fun SharinganColors.httpStatusTint(status: Int?): Tint = when {
    status == null -> Tint(err, errSoft)
    status >= 500 -> Tint(err, errSoft)
    status >= 400 -> Tint(warn, warnSoft)
    status >= 300 -> Tint(info, infoSoft)
    else -> Tint(ok, okSoft)
}

internal fun SharinganColors.httpMethodTint(method: String): Tint = when (method.uppercase()) {
    "GET" -> Tint(info, infoSoft)
    "POST" -> Tint(ok, okSoft)
    "PUT" -> Tint(warn, warnSoft)
    "PATCH" -> Tint(violet, violetSoft)
    "DELETE" -> Tint(err, errSoft)
    else -> Tint(textMid, faint)
}

internal fun SharinganColors.mqttDirectionTint(direction: MqttDirection): Tint = when (direction) {
    MqttDirection.PUBLISH -> Tint(ok, okSoft)
    MqttDirection.RECEIVE -> Tint(info, infoSoft)
    MqttDirection.SUBSCRIBE -> Tint(violet, violetSoft)
}

internal fun SharinganColors.bleOperationTint(operation: BleOperation): Tint = when (operation) {
    BleOperation.CONNECT -> Tint(info, infoSoft)
    BleOperation.DISCONNECT -> Tint(textDim, faint)
    BleOperation.DISCOVER -> Tint(violet, violetSoft)
    BleOperation.NOTIFY -> Tint(ok, okSoft)
    BleOperation.READ -> Tint(info, infoSoft)
    BleOperation.WRITE -> Tint(warn, warnSoft)
    BleOperation.ERROR -> Tint(err, errSoft)
}

internal fun presentationOf(colors: SharinganColors, event: SharinganEvent): EventPresentation =
    when (event) {
        is HttpEvent -> {
            val statusTint = colors.httpStatusTint(event.statusCode)
            EventPresentation(
                lead = event.method.uppercase(),
                leadTint = colors.httpMethodTint(event.method),
                main = event.path,
                sub = event.host,
                status = event.statusCode?.toString() ?: "ERR",
                statusTint = statusTint,
                right = event.durationMillis?.let { "${it}ms" },
                rightColor = null,
                sizeLabel = formatBytes(event.responseSizeBytes),
                clockTime = formatClockTime(event.timestampMillis),
                railColor = statusTint.color,
                isFailure = event.isFailure,
            )
        }
        is MqttEvent -> {
            val dirTint = colors.mqttDirectionTint(event.direction)
            EventPresentation(
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
        is BleEvent -> {
            val opTint = colors.bleOperationTint(event.operation)
            EventPresentation(
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
    }

/** `requests` / `messages` / `operations` — the column-header noun. */
internal fun Protocol.eventNoun(): String = when (this) {
    Protocol.HTTP -> "requests"
    Protocol.MQTT -> "messages"
    Protocol.BLE -> "operations"
}

/** `request` / `message` / `operation` — singular, for the share sheet title. */
internal fun Protocol.eventNounSingular(): String = eventNoun().dropLast(1)

/** Row time column: `mm:ss` slice of the clock time, like the design. */
internal fun rowTime(clockTime: String): String =
    if (clockTime.length >= 8) clockTime.substring(3, 8) else clockTime
