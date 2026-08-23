package dev.sharingan.db

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal actual class DriverFactory actual constructor() {
    actual fun create(): SqlDriver {
        val context = SharinganDbContext.appContext
            ?: error("Sharingan application context is unavailable; the manifest ContentProvider must initialize it first")
        return AndroidSqliteDriver(
            schema = SharinganDatabase.Schema,
            context = context,
            name = "sharingan.db",
            callback =
                object : AndroidSqliteDriver.Callback(SharinganDatabase.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )
    }
}
