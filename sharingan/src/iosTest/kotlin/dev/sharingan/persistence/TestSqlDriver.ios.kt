package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

internal actual fun createTestDriver(): SqlDriver =
    NativeSqliteDriver(
        schema = SharinganDatabase.Schema,
        name = "sharingan-test.db",
        onConfiguration = { it.copy(inMemory = true) },
    )
