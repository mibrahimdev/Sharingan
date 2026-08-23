package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal actual fun createTestDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        SharinganDatabase.Schema.create(this)
        execute(null, "PRAGMA foreign_keys=ON;", 0)
    }
