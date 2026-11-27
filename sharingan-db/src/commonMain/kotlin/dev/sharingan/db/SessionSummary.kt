package dev.sharingan.db

public data class SessionSummary(
    public val id: String,
    public val startedAtMillis: Long,
    public val eventCount: Int,
    public val failureCount: Int,
)
