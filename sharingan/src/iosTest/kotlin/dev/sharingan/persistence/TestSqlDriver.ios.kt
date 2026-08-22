package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

internal actual fun createTestDriver(): SqlDriver =
    NativeSqliteDriver(SharinganDatabase.Schema, "sharingan-test.db") {
        it.copy(inMemory = true)
    }
