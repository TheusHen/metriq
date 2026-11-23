package com.metriq.data

import kotlin.test.Test
import kotlin.test.assertEquals

class NutrientTargetTest {

    @Test
    fun `setting the same nutrient twice keeps a single up-to-date row`() {
        val store = newTestStore()
        store.nutrientTarget.set("protein", 100.0, null)
        store.nutrientTarget.set("protein", 120.0, null)

        val rows = store.nutrientTarget.all()
        assertEquals(1, rows.size)
        assertEquals("protein", rows.single().nutrient)
        assertEquals(120.0, rows.single().minimum)
    }

    @Test
    fun `different nutrients are kept separately`() {
        val store = newTestStore()
        store.nutrientTarget.set("protein", 100.0, null)
        store.nutrientTarget.set("carbs", null, 250.0)

        assertEquals(listOf("carbs", "protein"), store.nutrientTarget.all().map { it.nutrient })
    }
}
