package dev.sharingan.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform SQLDelight driver for the on-device flight-recorder
 * database. Internal: persistence is a debug-only concern and must not leak
 * into the public API (or the no-op twin).
 */
internal expect class DriverFactory() {
    fun create(): SqlDriver
}
