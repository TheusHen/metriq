package com.metriq.data

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupRepositoryTest {

    private fun populate(store: MetriqStore) {
        store.db.metriqQueries.upsertProfile(
            "MALE", 30L, 180.0, 80.0, 18.0,
            "MODERATE", "MAINTAIN", "BALANCED", "MODERATE", 78.0, 1_700_000_000_000L,
        )
        store.db.metriqQueries.insertWeight("2026-01-10", 80.5, "manual", "check-in")
        val foodId = store.food.insertCustomFood(
            "Backup food", "Acme", 123.0, 10.0, 2.0, 20.0, 1.0, 3.0, 50.0, "1 cup", 150.0,
        )
        store.db.evolutionQueries.insertFoodMicros(foodId, "{\"iron\":2.5}")
        store.logs.add("2026-01-11", 8, foodId, 150.0)
        store.settings.set("theme", "dark")
        store.settings.set("food_online_cache_v1", "{\"hits\":[]}")
        store.db.metriqQueries.upsertDayProperty("2026-01-11", 1L, 0L, "note with é")
        store.driver.execute(
            null,
            "INSERT INTO bodyMeasurement(date, site, valueCm) VALUES ('2026-01-12', 'waist', 82.5)",
            0,
        )
        store.driver.execute(null, "INSERT INTO habit(name, schedule, color) VALUES ('Run', 'daily', '#fff')", 0)
        store.driver.execute(null, "INSERT INTO habitLog(habitId, date, done) VALUES (1, '2026-01-12', 1)", 0)
    }

    @Test
    fun `export includes schema version, row counts and every user table`() {
        val store = newTestStore()
        populate(store)

        val json = JSONObject(BackupRepository(store.driver).exportJson())

        assertEquals(BackupRepository.FORMAT, json.getString("format"))
        assertEquals(MetriqMigrations.VERSION, json.getLong("schemaVersion"))
        val rowCounts = json.getJSONObject("rowCounts")
        assertEquals(1, rowCounts.getInt("profile"))
        assertEquals(1, rowCounts.getInt("logEntry"))
        assertEquals(2, rowCounts.getInt("settings"))
        val tables = json.getJSONObject("tables")
        listOf(
            "profile", "food", "foodMicros", "logEntry", "settings", "weightEntry",
            "dayProperty", "bodyMeasurement", "habit", "habitLog", "recipe", "program",
            "exportJob", "aiRequest",
        ).forEach { assertTrue(tables.has(it), "Missing table $it") }
        assertEquals(2, tables.getJSONObject("settings").getJSONArray("rows").length())
    }

    @Test
    fun `round trip restores representative data including settings keys`() {
        val source = newTestStore()
        populate(source)
        val backup = BackupRepository(source.driver).exportJson()

        val target = newTestStore()
        target.settings.set("obsolete", "will be replaced")
        val result = BackupRepository(target.driver).importJson(backup)

        val success = assertIs<ImportResult.Success>(result, (result as? ImportResult.Failure)?.reason ?: "")
        assertEquals(MetriqMigrations.VERSION, success.schemaVersion)
        assertEquals(1, success.rowCounts.getValue("logEntry"))
        assertEquals("MALE", target.db.metriqQueries.selectProfile().executeAsOne().sex)
        assertEquals(80.5, target.db.metriqQueries.selectWeights().executeAsOne().kg)
        assertEquals(1, target.food.count())
        assertEquals("{\"iron\":2.5}", target.food.micros(target.food.recent().single().id))
        assertEquals(1, target.logs.forDate("2026-01-11").size)
        assertEquals("dark", target.settings.get("theme"))
        assertEquals("{\"hits\":[]}", target.settings.get("food_online_cache_v1"))
        assertEquals(null, target.settings.get("obsolete"))
        assertEquals("note with é", target.db.metriqQueries.dayPropertyForDate("2026-01-11").executeAsOne().note)
        assertEquals(1L, target.driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM bodyMeasurement",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: -1L) },
            0,
        ).value)
    }

    @Test
    fun `version mismatch is rejected without touching existing data`() {
        val target = newTestStore()
        populate(target)
        val before = BackupRepository(target.driver).exportJson()
        val json = JSONObject(before).apply { put("schemaVersion", MetriqMigrations.VERSION + 1) }

        val result = BackupRepository(target.driver).importJson(json.toString())

        val failure = assertIs<ImportResult.Failure>(result)
        assertTrue(failure.reason.contains("version", ignoreCase = true))
        assertEquals(before, BackupRepository(target.driver).exportJson())
    }

    @Test
    fun `malformed JSON and wrong shape are rejected without touching existing data`() {
        val target = newTestStore()
        populate(target)
        val before = BackupRepository(target.driver).exportJson()

        assertIs<ImportResult.Failure>(BackupRepository(target.driver).importJson("{"))
        assertIs<ImportResult.Failure>(BackupRepository(target.driver).importJson("[]"))
        assertIs<ImportResult.Failure>(
            BackupRepository(target.driver).importJson(JSONObject(before).remove("tables").toString()),
        )
        assertEquals(before, BackupRepository(target.driver).exportJson())
    }

    @Test
    fun `a downstream SQL failure rolls the whole import back`() {
        val source = newTestStore()
        populate(source)
        val target = newTestStore()
        populate(target)
        val before = BackupRepository(target.driver).exportJson()

        val tampered = JSONObject(BackupRepository(source.driver).exportJson())
        tampered.getJSONObject("tables").getJSONObject("settings")
            .getJSONArray("rows").getJSONObject(0).put("value", JSONObject.NULL)

        val result = BackupRepository(target.driver).importJson(tampered.toString())

        assertIs<ImportResult.Failure>(result)
        assertEquals(before, BackupRepository(target.driver).exportJson())
        assertFalse((result as ImportResult.Failure).reason.isBlank())
    }

    @Test
    fun `import refuses a backup that is missing a table present locally`() {
        val source = newTestStore()
        populate(source)
        val target = newTestStore()
        populate(target)
        val before = BackupRepository(target.driver).exportJson()
        val tampered = JSONObject(BackupRepository(source.driver).exportJson())
        tampered.getJSONObject("tables").remove("settings")

        assertIs<ImportResult.Failure>(BackupRepository(target.driver).importJson(tampered.toString()))
        assertEquals(before, BackupRepository(target.driver).exportJson())
    }
}
