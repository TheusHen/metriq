package com.metriq.data

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.metriq.data.db.MetriqDatabase

/**
 * Explicit, versioned database migrations.
 *
 * Every step is additive (CREATE ... IF NOT EXISTS) so upgrading can never drop or
 * rewrite an existing table. The steps are plain SQL strings, which keeps them
 * testable with the JVM SQLite driver as well as runnable from the Android callback.
 */
object MetriqMigrations {

    /** Schema version 1 is the legacy, unversioned database. */
    const val VERSION = 2L

    /** Maps the version being upgraded FROM to the SQL applied on the way to the next version. */
    private val STEPS: Map<Long, List<String>> = mapOf(
        1L to listOf(
            "CREATE VIRTUAL TABLE IF NOT EXISTS foodFts USING fts5(name, brand, category, foodId UNINDEXED)",
            "CREATE TABLE IF NOT EXISTS foodMicros(foodId INTEGER PRIMARY KEY REFERENCES food(id), json TEXT NOT NULL)",
        ),
    )

    fun migrate(oldVersion: Long, newVersion: Long, exec: (String) -> Unit) {
        (oldVersion until newVersion).forEach { from -> STEPS[from]?.forEach(exec) }
    }
}

/**
 * Pins the on-device schema version at [MetriqMigrations.VERSION]. SQLDelight's generated
 * schema stays at version 1, so wrapping it is what makes the upgrade callback fire and
 * guarantees upgrades go through the additive steps above instead of a silent recreate.
 */
object MetriqSchema : SqlSchema<QueryResult.Value<Unit>> by MetriqDatabase.Schema {

    override val version: Long get() = MetriqMigrations.VERSION

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
        MetriqMigrations.migrate(oldVersion, newVersion) { sql -> driver.execute(null, sql, 0) }
        return QueryResult.Unit
    }
}
