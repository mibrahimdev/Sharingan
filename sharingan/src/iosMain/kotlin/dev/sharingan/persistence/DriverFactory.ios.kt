package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

internal actual class DriverFactory actual constructor() {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(
            schema = SharinganDatabase.Schema,
            name = "sharingan.db",
            onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(foreignKeyConstraints = true)) },
        )
}
