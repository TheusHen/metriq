package com.metriq.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FoodSeedTest {

    private fun pack(): String {
        val header = List(26) { "h$it" }.toMutableList().apply { this[0] = "name" }
        fun row(name: String, kcal: String) = List(26) { "" }.toMutableList().apply {
            this[0] = name
            this[1] = "Acme"
            this[2] = "snack"
            this[4] = kcal
            this[5] = "5"
        }
        return listOf(header, row("Seed A", "100"), row("Seed B", "200"))
            .joinToString("\n") { it.joinToString("\t") }
    }

    @Test
    fun `an interrupted seed is completed without duplicating the rows already written`() {
        val store = newTestStore()
        // Simulate a crash after the first insert but before the marker was written.
        store.db.metriqQueries.insertFood(
            source = "base", sourceId = "Seed A", barcode = null, name = "Seed A",
            brand = "Acme", category = "snack", iconId = null,
            calories = 100.0, protein = 5.0, fat = 1.0, carbs = 10.0,
            fiber = null, sugar = null, sodium = null,
            servingLabel = null, servingGrams = null, verified = 1L, isCustom = 0L,
        )

        store.food.seedIfNeeded(pack())

        assertEquals(2, store.food.count())
        assertEquals(
            setOf("Seed A", "Seed B"),
            store.food.recent().map { it.name }.toSet(),
        )

        // Marker now present: a later seed call must be a no-op.
        store.food.seedIfNeeded(pack())
        assertEquals(2, store.food.count())
    }

    @Test
    fun `seed marker prevents overwriting custom foods added after seeding`() {
        val store = newTestStore()
        store.food.seedIfNeeded(pack())
        store.food.insertCustomFood("My food", null, 1.0, 1.0, 1.0, 1.0, null, null, null, null, null)

        store.food.seedIfNeeded("name\th\theader\nCustom\tBrand\tx\t1\t1\t1\t1")

        assertEquals(3, store.food.count())
        assertEquals("My food", store.food.recent().first().name)
    }
}
