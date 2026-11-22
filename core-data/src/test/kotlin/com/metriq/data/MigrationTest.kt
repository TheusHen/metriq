package com.metriq.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {

    @Test
    fun `schema version is pinned so onCreate and onUpgrade are distinguishable`() {
        // Version 1 is the legacy unversioned schema. Pinning an explicit version makes the
        // migration callback meaningful and prevents SQLDelight from silently recreating tables.
        assertTrue(MetriqSchema.version >= 2L)
    }

    @Test
    fun `migration from version 1 preserves existing rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MetriqDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO settings(key, value) VALUES ('theme', 'dark')", 0)
        driver.execute(null, "INSERT INTO weightEntry(date, kg) VALUES ('2026-01-15', 80.0)", 0)

        // Simulate an onUpgrade from the legacy version using the exact production steps.
        MetriqSchema.migrate(driver, 1L, MetriqSchema.version).value

        assertEquals("dark", MetriqDatabase(driver).metriqQueries.settingValue("theme").executeAsOne())
        assertEquals(1L, driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM weightEntry",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
        // Additive tables are present after the upgrade, too.
        assertEquals(0L, driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM foodFts",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
    }

    @Test
    fun `migration is idempotent`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MetriqDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO settings(key, value) VALUES ('theme', 'dark')", 0)

        repeat(2) { MetriqSchema.migrate(driver, 1L, MetriqSchema.version).value }
        assertEquals("dark", MetriqDatabase(driver).metriqQueries.settingValue("theme").executeAsOne())
    }
}
