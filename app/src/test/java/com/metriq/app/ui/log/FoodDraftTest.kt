package com.metriq.app.ui.log

import com.metriq.data.FoodHit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression proof for the double-multiplication bug: drafts must carry raw per-100 g
 * macros and the chosen serving grams, because LogRepository scales by grams/100.
 */
class FoodDraftTest {

    private fun milk(grams: Double? = 100.0) = FoodHit(
        id = 1L,
        name = "Milk",
        brand = null,
        category = "dairy",
        iconId = null,
        calories = 60.0,
        protein = 3.2,
        fat = 3.6,
        carbs = 4.8,
        servingLabel = "1 cup",
        servingGrams = grams,
    )

    @Test
    fun `draft keeps raw catalog macros and only overrides serving grams`() {
        val draft = milk().toFoodDraft(200.0, "1 serving")

        assertEquals(60.0, draft.calories, 0.0)
        assertEquals(3.2, draft.protein, 0.0)
        assertEquals(3.6, draft.fat, 0.0)
        assertEquals(4.8, draft.carbs, 0.0)
        assertEquals(200.0, draft.servingGrams, 0.0)
        assertEquals("1 cup", draft.servingLabel)
    }

    @Test
    fun `logged calories apply the grams factor exactly once`() {
        val draft = milk().toFoodDraft(200.0, "1 serving")
        // Mirrors LogRepository.forDate: kcal = stored per-100 g kcal * grams / 100.
        val logged = draft.calories * draft.servingGrams / 100.0

        assertEquals(120.0, logged, 0.0)
    }

    @Test
    fun `preview sums use serving grams instead of raw per 100 g values`() {
        assertEquals(60, listOf(milk()).previewKcal())
        assertEquals(240, listOf(milk(200.0), milk(200.0)).previewKcal())
        assertEquals(60, listOf(milk(null)).previewKcal())
    }

    @Test
    fun `invalid serving grams fall back to 100 grams`() {
        assertEquals(100.0, milk(0.0).effectiveGrams(), 0.0)
        assertEquals(100.0, milk(-5.0).effectiveGrams(), 0.0)
    }
}
