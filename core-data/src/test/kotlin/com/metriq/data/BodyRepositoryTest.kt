package com.metriq.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyRepositoryTest {

    private fun store(): MetriqStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MetriqDatabase.Schema.create(driver)
        return MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier())
    }

    @Test
    fun `allSites returns distinct sorted sites`() {
        val store = store()
        store.body.add("2026-01-01", "waist", 80.0)
        store.body.add("2026-01-02", "arm", 30.0)
        store.body.add("2026-01-03", "waist", 79.5)

        assertEquals(listOf("arm", "waist"), store.body.allSites())
    }
}
