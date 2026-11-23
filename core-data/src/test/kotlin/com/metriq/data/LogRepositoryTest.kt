package com.metriq.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LogRepositoryTest {

    @Test
    fun `a malformed legacy log date does not break dailyIntake`() {
        val store = newTestStore()
        val id = store.food.insertCustomFood(
            "Oats", null, 100.0, 10.0, 2.0, 15.0, null, null, null, null, null,
        )
        store.logs.add("2026-03-10", 8, id, 100.0)
        // Legacy row with a non-ISO date must be skipped, not crash the dashboard.
        store.db.metriqQueries.insertLogEntry("not-a-date", 8L, id, 100.0, 1.0, 0L)

        val intake = store.logs.dailyIntake()
        assertEquals(mapOf(LocalDate.of(2026, 3, 10) to 100.0), intake)
    }

    @Test
    fun `updateGrams changes stored grams and recalculates macros`() {
        val store = newTestStore()
        val foodId = store.food.insertCustomFood(
            "Oats", null, 100.0, 10.0, 2.0, 15.0, null, null, null, null, null,
        )
        store.logs.add("2026-05-01", 8, foodId, 100.0)
        val logId = store.logs.forDate("2026-05-01").single().id

        store.logs.updateGrams(logId, 150.0)

        val item = store.logs.forDate("2026-05-01").single()
        assertEquals(150.0, item.grams, 0.0)
        assertEquals(150.0, item.calories, 0.0)
        assertEquals(15.0, item.protein, 0.0)
        assertEquals(3.0, item.fat, 0.0)
        assertEquals(22.5, item.carbs, 0.0)
    }

    @Test
    fun `updateGrams notifies food log watchers`() = runBlocking {
        val store = newTestStore()
        val foodId = store.food.insertCustomFood(
            "Rice", null, 130.0, 3.0, 1.0, 28.0, null, null, null, null, null,
        )
        store.db.metriqQueries.insertLogEntry("2026-05-02", 8L, foodId, 100.0, 1.0, 0L)
        val logId = store.db.metriqQueries.logsForDate("2026-05-02").executeAsOne().id
        // Fresh notifier: the seed insert above must not be mistaken for the update event.
        val watcher = DataChangeNotifier()
        val logs = LogRepository(store.db, watcher)

        logs.updateGrams(logId, 200.0)

        val event = withTimeout(1000) { watcher.events.first() }
        assertEquals(DataType.FOOD_LOG, event.type)
        assertEquals(200.0, logs.forDate("2026-05-02").single().grams, 0.0)
    }

    @Test
    fun `copying to a day twice is idempotent`() {
        val store = newTestStore()
        val id = store.food.insertCustomFood(
            "Rice", null, 130.0, 3.0, 1.0, 28.0, null, null, null, null, null,
        )
        store.logs.add("2026-04-01", 8, id, 100.0)
        store.logs.add("2026-04-01", 13, id, 150.0)
        store.logs.add("2026-04-02", 9, id, 50.0) // pre-existing row on target

        store.logs.copy("2026-04-01", "2026-04-02")
        store.logs.copy("2026-04-01", "2026-04-02")

        assertEquals(2, store.logs.forDate("2026-04-02").size)
        assertEquals(2, store.logs.forDate("2026-04-01").size)
    }
}
