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
    const val VERSION = 4L

    /** Maps the version being upgraded FROM to the SQL applied on the way to the next version. */
    private val STEPS: Map<Long, List<String>> = mapOf(
        1L to listOf(
            "CREATE TABLE IF NOT EXISTS foodMicros(foodId INTEGER PRIMARY KEY REFERENCES food(id), json TEXT NOT NULL)",
        ),
        2L to listOf(
            // SQLite cannot ALTER a table to add UNIQUE, so clean up legacy duplicates
            // first and then add the constraint as an index (additive, data-preserving).
            "DELETE FROM nutrientTarget WHERE id NOT IN (SELECT MIN(id) FROM nutrientTarget GROUP BY nutrient)",
            "CREATE UNIQUE INDEX IF NOT EXISTS nutrientTarget_nutrient ON nutrientTarget(nutrient)",
        ),
        3L to listOf(
            // Purely additive: no table is rewritten and no row is deleted. The same
            // indexes are declared in Metriq.sq so fresh installs get them too.
            "CREATE INDEX IF NOT EXISTS bodyMeasurement_date ON bodyMeasurement(date)",
            "CREATE INDEX IF NOT EXISTS progressPhoto_date ON progressPhoto(date)",
            "CREATE INDEX IF NOT EXISTS habitLog_date ON habitLog(date)",
            "CREATE INDEX IF NOT EXISTS food_name_lower ON food(lower(name))",
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
