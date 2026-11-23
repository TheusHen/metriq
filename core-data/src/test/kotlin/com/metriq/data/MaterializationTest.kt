package com.metriq.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaterializationTest {

    private fun hit(id: Long = -42L) = FoodHit(
        id = id,
        name = "Online Crème fraîche",
        brand = "Test Brand",
        category = "dairy",
        iconId = null,
        calories = 200.0,
        protein = 3.0,
        fat = 20.0,
        carbs = 2.0,
        servingLabel = "1 pot",
        servingGrams = 100.0,
    )

    @Test
    fun `materialize gives a negative-id hit a real id that is loggable`() {
        val store = newTestStore()
        val realId = store.food.materialize(hit())
        assertTrue(realId > 0, "Expected a real positive id, got $realId")

        store.logs.add("2026-02-01", 12, realId, 100.0)

        val logged = store.logs.forDate("2026-02-01")
        assertEquals(1, logged.size)
        assertEquals(realId, logged.single().foodId)
        assertEquals("Online Crème fraîche", logged.single().name)
        assertEquals(200.0, store.logs.dailyIntake()[LocalDate.of(2026, 2, 1)] ?: 0.0, 0.001)
    }

    @Test
    fun `materialize is idempotent and keeps positive ids unchanged`() {
        val store = newTestStore()
        val synthetic = hit()
        val first = store.food.materialize(synthetic)
        val second = store.food.materialize(synthetic)
        assertEquals(first, second)
        assertEquals(1L, store.food.count())

        assertEquals(999L, store.food.materialize(synthetic.copy(id = 999L)))
        assertEquals(1L, store.food.count())
    }

    @Test
    fun `logging a synthetic food id directly is rejected`() {
        val store = newTestStore()

        assertFailsWith<IllegalArgumentException> {
            store.logs.add("2026-02-06", 9, -42L, 100.0)
        }
        assertFailsWith<IllegalArgumentException> {
            store.logs.add("2026-02-06", 9, 0L, 100.0)
        }
        assertTrue(store.logs.forDate("2026-02-06").isEmpty())
    }
}
