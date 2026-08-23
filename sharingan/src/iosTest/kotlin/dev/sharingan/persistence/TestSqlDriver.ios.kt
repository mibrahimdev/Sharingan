package dev.sharingan.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal actual fun createTestDriver(): SqlDriver =
    NativeSqliteDriver(
        schema = SharinganDatabase.Schema,
        name = "sharingan-test-${Uuid.random()}.db",
        onConfiguration = {
            it.copy(
                inMemory = true,
                extendedConfig = it.extendedConfig.copy(foreignKeyConstraints = true),
            )
        },
    )
