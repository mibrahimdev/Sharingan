package dev.sharingan.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun formatClockTime(epochMillis: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMillis))

internal actual fun formatDateTime(epochMillis: Long): String = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(epochMillis))
