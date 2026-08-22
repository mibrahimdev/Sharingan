package dev.sharingan.persistence

import dev.sharingan.BleEvent
import dev.sharingan.HttpEvent
import dev.sharingan.MqttEvent
import dev.sharingan.SharinganEvent
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of [SharinganEvent], stored as the `event.payload_json`
 * blob. A SEPARATE internal type rather than `@Serializable` on the public
 * events — the #15 ABI freeze forbids touching `HttpEvent`/`MqttEvent`/`BleEvent`.
 *
 * Encode-only this slice: [fromEvent] maps a public event to its DTO. The
 * `toEvent()` decoder lands in slice 3 (in-viewer history), which is why every
 * public field has a mirrored counterpart and enum values are carried by name.
 */
@Serializable
internal sealed interface EventDto {
    val id: String
    val timestampMillis: Long
    val error: String?

    companion object {
        fun fromEvent(event: SharinganEvent): EventDto = when (event) {
            is HttpEvent -> HttpDto(
                id = event.id,
                timestampMillis = event.timestampMillis,
                method = event.method,
                url = event.url,
                statusCode = event.statusCode,
                durationMillis = event.durationMillis,
                requestHeaders = event.requestHeaders.map { (name, value) -> HeaderDto(name, value) },
                responseHeaders = event.responseHeaders.map { (name, value) -> HeaderDto(name, value) },
                requestBody = event.requestBody,
                responseBody = event.responseBody,
                contentType = event.contentType,
                responseSizeBytes = event.responseSizeBytes,
                timing = event.timing.map { TimingPhaseDto(it.label, it.millis) },
                error = event.error,
            )
            is MqttEvent -> MqttDto(
                id = event.id,
                timestampMillis = event.timestampMillis,
                direction = event.direction.name,
                topic = event.topic,
                qos = event.qos,
                retained = event.retained,
                payload = event.payload,
                payloadSizeBytes = event.payloadSizeBytes,
                error = event.error,
            )
            is BleEvent -> BleDto(
                id = event.id,
                timestampMillis = event.timestampMillis,
                operation = event.operation.name,
                device = event.device,
                characteristic = event.characteristic,
                uuid = event.uuid,
                payload = event.payload,
                sizeBytes = event.sizeBytes,
                error = event.error,
            )
        }
    }
}

@Serializable
internal data class HeaderDto(val name: String, val value: String)

@Serializable
internal data class TimingPhaseDto(val label: String, val millis: Long)

@Serializable
internal data class HttpDto(
    override val id: String,
    override val timestampMillis: Long,
    val method: String,
    val url: String,
    val statusCode: Int? = null,
    val durationMillis: Long? = null,
    val requestHeaders: List<HeaderDto> = emptyList(),
    val responseHeaders: List<HeaderDto> = emptyList(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val contentType: String? = null,
    val responseSizeBytes: Long? = null,
    val timing: List<TimingPhaseDto> = emptyList(),
    override val error: String? = null,
) : EventDto

@Serializable
internal data class MqttDto(
    override val id: String,
    override val timestampMillis: Long,
    val direction: String,
    val topic: String,
    val qos: Int = 0,
    val retained: Boolean = false,
    val payload: String? = null,
    val payloadSizeBytes: Long? = null,
    override val error: String? = null,
) : EventDto

@Serializable
internal data class BleDto(
    override val id: String,
    override val timestampMillis: Long,
    val operation: String,
    val device: String,
    val characteristic: String? = null,
    val uuid: String? = null,
    val payload: String? = null,
    val sizeBytes: Long? = null,
    override val error: String? = null,
) : EventDto
