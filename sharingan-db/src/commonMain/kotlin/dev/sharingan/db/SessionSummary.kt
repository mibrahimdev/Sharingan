package dev.sharingan.db

/** One row in the session picker: a past session's launch timestamp and event counts. */
public data class SessionSummary(
    public val id: String,
    public val startedAtMillis: Long,
    public val eventCount: Int,
    public val failureCount: Int,
)
