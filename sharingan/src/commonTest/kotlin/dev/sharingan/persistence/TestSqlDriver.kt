package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver

internal expect fun createTestDriver(): SqlDriver
