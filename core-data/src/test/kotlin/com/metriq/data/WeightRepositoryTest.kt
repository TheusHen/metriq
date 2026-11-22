package com.metriq.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class WeightRepositoryTest {

    private fun store(): MetriqStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MetriqDatabase.Schema.create(driver)
        return MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier())
    }

    @Test
    fun `a malformed legacy date does not break all`() {
        val store = store()
        store.weights.add("2026-01-15", 80.0)
        // Legacy rows may contain non-ISO text; they must not brick the dashboard.
        store.db.metriqQueries.insertWeight("not-a-date", 999.0, "legacy", null)

        val entries = store.weights.all()
        assertEquals(1, entries.size)
        assertEquals("2026-01-15", entries.single().date.toString())
        assertEquals(80.0, entries.single().kg)
    }
}
