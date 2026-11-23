package com.metriq.data

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
