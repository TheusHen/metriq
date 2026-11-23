package com.metriq.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HabitRepositoryTest {

    private fun newHabit(): Pair<MetriqStore, Long> {
        val store = newTestStore()
        store.habit.add("Exercise")
        return store to store.habit.active().single().id
    }

    @Test
    fun `streak stops at a gap`() {
        val (store, id) = newHabit()
        val today = LocalDate.of(2026, 5, 10)
        store.db.transaction {
            // 4 consecutive days, then a gap, then an older done day.
            repeat(4) { offset -> store.db.evolutionQueries.toggleHabitLog(id, today.minusDays(offset.toLong()).toString(), 1L) }
            store.db.evolutionQueries.toggleHabitLog(id, today.minusDays(6).toString(), 1L)
        }
        assertEquals(4, store.habit.streak(id, today.toString()))
    }

    @Test
    fun `streak is capped at 3650 days`() {
        val (store, id) = newHabit()
        val today = LocalDate.of(2026, 5, 10)
        store.db.transaction {
            repeat(3700) { offset ->
                store.db.evolutionQueries.toggleHabitLog(id, today.minusDays(offset.toLong()).toString(), 1L)
            }
        }
        assertEquals(3650, store.habit.streak(id, today.toString()))
    }
}
