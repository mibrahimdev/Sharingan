package dev.sharingan.internal

/** Current wall-clock time in epoch milliseconds. */
internal expect fun currentTimeMillis(): Long

/** Formats epoch milliseconds as local `HH:mm:ss.SSS`, e.g. `12:04:31.882`. */
internal expect fun formatClockTime(epochMillis: Long): String
