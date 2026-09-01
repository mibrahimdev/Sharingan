package dev.sharingan.db

import app.cash.sqldelight.db.SqlDriver

internal expect fun createTestDriver(): SqlDriver
