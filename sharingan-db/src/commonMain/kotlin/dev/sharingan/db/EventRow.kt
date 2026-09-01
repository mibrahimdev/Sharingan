package dev.sharingan.db

/**
 * Plain row handed to the persistence controller. The caller's [toRow] mapping
 * runs on the flusher coroutine so the hot record() path stays a lock-free
 * channel send.
 */
public data class EventRow(
    val rawId: String,
    val timestampMillis: Long,
    val type: String,
    val isFailure: Boolean,
    val hostOrTopic: String?,
    val payloadJson: String,
)
