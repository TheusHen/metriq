package com.metriq.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnicodeSearchTest {

    private fun storeWithAccentedFoods(): MetriqStore {
        val store = newTestStore()
        store.food.insertCustomFood("açúcar", null, 10.0, 0.0, 0.0, 0.0, null, null, null, null, null)
        store.food.insertCustomFood("Carrot", null, 20.0, 0.0, 0.0, 0.0, null, null, null, null, null)
        store.food.insertCustomFood("Crème fraîche", null, 30.0, 0.0, 0.0, 0.0, null, null, null, null, null)
        return store
    }

    @Test
    fun `accented query matches the accented food but not unrelated ascii foods`() {
        val store = storeWithAccentedFoods()
        val names = store.food.search("açúcar").map { it.name }
        assertEquals(listOf("açúcar"), names)
    }

    @Test
    fun `accented names remain searchable from the online cache`() {
        val store = newTestStore()
        store.food.cacheOnline(
            listOf(
                FoodHit(-1, "Crème fraîche", null, null, null, 10.0, 1.0, 1.0, 1.0, null, null),
                FoodHit(-2, "Carrot cake", null, null, null, 10.0, 1.0, 1.0, 1.0, null, null),
            ),
        )
        val names = store.food.cachedOnline("crème").map { it.name }
        assertEquals(listOf("Crème fraîche"), names)
        assertTrue(names.isNotEmpty())
    }
}
