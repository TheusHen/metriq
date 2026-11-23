package com.metriq.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase

/** Shared in-memory store builder for the regression tests in this module. */
internal fun newTestStore(): MetriqStore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    MetriqDatabase.Schema.create(driver)
    return MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier())
}
