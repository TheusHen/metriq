package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Foreign-key enforcement helpers shared by the Android driver callback and by
 * generic (JVM) drivers used in tests and by the backup/restore transaction.
 *
 * SQLite only honours `PRAGMA foreign_keys` when it is executed *outside* a
 * transaction, so callers must toggle it before BEGIN and restore it after
 * COMMIT/ROLLBACK.
 */
object ForeignKeys {

    fun setEnabled(driver: SqlDriver, enabled: Boolean) {
        driver.execute(null, if (enabled) "PRAGMA foreign_keys = ON" else "PRAGMA foreign_keys = OFF", 0)
    }

    fun isEnabled(driver: SqlDriver): Boolean = driver.executeQuery(
        null,
        "PRAGMA foreign_keys",
        { cursor -> QueryResult.Value((cursor.getLong(0) ?: 0L) != 0L) },
        0,
    ).value
}
