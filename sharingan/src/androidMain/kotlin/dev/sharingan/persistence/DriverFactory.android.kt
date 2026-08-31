package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.sharingan.internal.SharinganAndroid

internal actual class DriverFactory {
    actual fun create(): SqlDriver {
        val context =
            SharinganAndroid.appContext
                ?: error("Sharingan application context is unavailable; the manifest ContentProvider must initialize it first")
        return AndroidSqliteDriver(SharinganDatabase.Schema, context, "sharingan.db")
    }
}
