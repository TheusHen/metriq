package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForeignKeyTest {

    private fun countLogs(driver: SqlDriver): Long = driver.executeQuery(
        null,
        "SELECT COUNT(*) FROM logEntry",
        { cursor -> QueryResult.Value(cursor.getLong(0) ?: -1L) },
        0,
    ).value

    @Test
    fun `with foreign keys enabled a log with a missing food id is rejected`() {
        val store = newTestStore()
        ForeignKeys.setEnabled(store.driver, true)
        assertTrue(ForeignKeys.isEnabled(store.driver))

        assertFailsWith<Exception> {
            store.logs.add("2026-03-01", 12, 9_999L, 100.0)
        }
        assertEquals(0L, countLogs(store.driver))
    }

    @Test
    fun `a valid food id is still loggable with foreign keys enabled`() {
        val store = newTestStore()
        ForeignKeys.setEnabled(store.driver, true)
        val id = store.food.insertCustomFood("FK test", null, 1.0, 1.0, 1.0, 1.0, null, null, null, null, null)

        store.logs.add("2026-03-02", 12, id, 100.0)

        assertEquals(1L, countLogs(store.driver))
    }

    @Test
    fun `no existing table declares ON DELETE CASCADE`() {
        val store = newTestStore()
        val count = store.driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND UPPER(COALESCE(sql, '')) LIKE '%ON DELETE CASCADE%'",
            { cursor -> QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value
        assertFalse(count > 0L, "Adding cascade deletes requires a table rebuild; none must be added here")
    }
}
