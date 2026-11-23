package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationTest {

    /**
     * The genuine legacy (version 1) schema: every table that existed on device
     * *except* foodMicros, which only the migration adds. Using the current generated
     * schema here would hide a broken CREATE TABLE foodMicros.
     */
    private fun createLegacySchema(driver: SqlDriver) {
        listOf(
            """CREATE TABLE profile (
                id INTEGER PRIMARY KEY CHECK (id = 1), sex TEXT NOT NULL, age INTEGER NOT NULL,
                heightCm REAL NOT NULL, weightKg REAL NOT NULL, bodyFatPercent REAL,
                activity TEXT NOT NULL, goal TEXT NOT NULL, diet TEXT NOT NULL,
                proteinTier TEXT NOT NULL, targetWeightKg REAL, createdAt INTEGER NOT NULL)""",
            """CREATE TABLE weightEntry (
                id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL UNIQUE, kg REAL NOT NULL,
                source TEXT NOT NULL DEFAULT 'manual', note TEXT)""",
            """CREATE TABLE food (
                id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, sourceId TEXT,
                barcode TEXT, name TEXT NOT NULL, brand TEXT, category TEXT, iconId TEXT,
                calories REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL, carbs REAL NOT NULL,
                fiber REAL, sugar REAL, sodium REAL, servingLabel TEXT, servingGrams REAL,
                verified INTEGER NOT NULL DEFAULT 0, isCustom INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE logEntry (
                id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, hour INTEGER NOT NULL,
                foodId INTEGER NOT NULL REFERENCES food(id), grams REAL NOT NULL,
                servings REAL NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)""",
            """CREATE TABLE recipe (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, servings INTEGER NOT NULL,
                totalGrams REAL NOT NULL, note TEXT)""",
            """CREATE TABLE recipeItem (
                id INTEGER PRIMARY KEY AUTOINCREMENT, recipeId INTEGER NOT NULL REFERENCES recipe(id),
                foodId INTEGER NOT NULL REFERENCES food(id), grams REAL NOT NULL)""",
            """CREATE TABLE dayProperty (
                date TEXT PRIMARY KEY, onPeriod INTEGER NOT NULL DEFAULT 0,
                fasting INTEGER NOT NULL DEFAULT 0, note TEXT)""",
            """CREATE TABLE bodyMeasurement (
                id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, site TEXT NOT NULL,
                valueCm REAL NOT NULL)""",
            """CREATE TABLE progressPhoto (
                id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, path TEXT NOT NULL,
                pose TEXT, note TEXT)""",
            """CREATE TABLE habit (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, schedule TEXT NOT NULL,
                color TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE habitLog (
                habitId INTEGER NOT NULL REFERENCES habit(id), date TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 1, PRIMARY KEY (habitId, date))""",
            """CREATE TABLE stepEntry (
                date TEXT PRIMARY KEY, steps INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'manual')""",
            """CREATE TABLE waterEntry (date TEXT PRIMARY KEY, ml INTEGER NOT NULL)""",
            """CREATE TABLE nutrientTarget (
                id INTEGER PRIMARY KEY AUTOINCREMENT, nutrient TEXT NOT NULL, mode TEXT NOT NULL,
                minimum REAL, maximum REAL, effectiveFrom TEXT NOT NULL)""",
            """CREATE TABLE program (
                id INTEGER PRIMARY KEY AUTOINCREMENT, style TEXT NOT NULL, startDate TEXT NOT NULL,
                weeklyKcal REAL NOT NULL, targetRatePercent REAL NOT NULL, split TEXT)""",
            """CREATE TABLE checkin (
                id INTEGER PRIMARY KEY AUTOINCREMENT, weekStart TEXT NOT NULL UNIQUE,
                recommendedKcal REAL NOT NULL, recommendedMacros TEXT NOT NULL,
                accepted INTEGER NOT NULL DEFAULT 0, note TEXT)""",
            """CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)""",
            """CREATE TABLE exportJob (
                id INTEGER PRIMARY KEY AUTOINCREMENT, scope TEXT NOT NULL, rangeStart TEXT NOT NULL,
                rangeEnd TEXT NOT NULL, format TEXT NOT NULL, path TEXT NOT NULL,
                createdAt INTEGER NOT NULL)""",
            """CREATE TABLE aiRequest (
                id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, model TEXT NOT NULL,
                tokens INTEGER NOT NULL, latencyMs INTEGER NOT NULL, local INTEGER NOT NULL,
                createdAt INTEGER NOT NULL)""",
            "CREATE INDEX weightEntry_date ON weightEntry(date)",
            "CREATE UNIQUE INDEX food_barcode ON food(barcode) WHERE barcode IS NOT NULL",
            "CREATE INDEX logEntry_date ON logEntry(date)",
        ).forEach { driver.execute(null, it, 0) }
    }

    private fun objectExists(driver: SqlDriver, name: String): Boolean = driver.executeQuery(
        null,
        "SELECT COUNT(*) FROM sqlite_master WHERE name = ?",
        { cursor -> QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L) },
        1,
    ) { bindString(0, name) }.value

    @Test
    fun `schema version is pinned so onCreate and onUpgrade are distinguishable`() {
        assertTrue(MetriqSchema.version >= 2L)
    }

    @Test
    fun `migration from version 1 preserves existing rows and adds foodMicros`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacySchema(driver)
        assertFalse(objectExists(driver, "foodMicros"), "v1 must not contain foodMicros")

        driver.execute(null, "INSERT INTO settings(key, value) VALUES ('theme', 'dark')", 0)
        driver.execute(null, "INSERT INTO weightEntry(date, kg) VALUES ('2026-01-15', 80.0)", 0)
        driver.execute(
            null,
            "INSERT INTO nutrientTarget(nutrient, mode, effectiveFrom) VALUES ('protein', 'min', '2026-01-01')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO nutrientTarget(nutrient, mode, effectiveFrom) VALUES ('protein', 'min', '2026-02-01')",
            0,
        )

        MetriqSchema.migrate(driver, 1L, MetriqSchema.version).value

        assertEquals("dark", MetriqDatabase(driver).metriqQueries.settingValue("theme").executeAsOne())
        assertEquals(1L, driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM weightEntry",
            { cursor -> QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
        assertTrue(objectExists(driver, "foodMicros"))
        assertTrue(objectExists(driver, "nutrientTarget_nutrient"))
        // Legacy duplicates were collapsed while preserving the oldest row.
        assertEquals(1L, driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM nutrientTarget",
            { cursor -> QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
        assertEquals("2026-01-01", MetriqDatabase(driver).evolutionQueries
            .selectNutrientTargets().executeAsOne().effectiveFrom)
    }

    @Test
    fun `index migration adds date and food name indexes without touching existing rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacySchema(driver)
        driver.execute(null, "INSERT INTO settings(key, value) VALUES ('theme', 'light')", 0)
        driver.execute(
            null,
            "INSERT INTO bodyMeasurement(date, site, valueCm) VALUES ('2026-04-01', 'waist', 80.0)",
            0,
        )

        MetriqSchema.migrate(driver, 1L, MetriqSchema.version).value
        // Running twice must be harmless (CREATE ... IF NOT EXISTS).
        MetriqSchema.migrate(driver, MetriqSchema.version - 1, MetriqSchema.version).value

        listOf("bodyMeasurement_date", "progressPhoto_date", "habitLog_date", "food_name_lower")
            .forEach { assertTrue(objectExists(driver, it), "Missing index $it") }
        assertEquals("light", MetriqDatabase(driver).metriqQueries.settingValue("theme").executeAsOne())
        assertEquals(1L, driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM bodyMeasurement",
            { cursor -> QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
    }

    @Test
    fun `migration is idempotent`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createLegacySchema(driver)
        driver.execute(null, "INSERT INTO settings(key, value) VALUES ('theme', 'dark')", 0)

        repeat(2) { MetriqSchema.migrate(driver, 1L, MetriqSchema.version).value }
        assertEquals("dark", MetriqDatabase(driver).metriqQueries.settingValue("theme").executeAsOne())
        assertTrue(objectExists(driver, "foodMicros"))
    }
}
