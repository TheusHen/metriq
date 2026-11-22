package com.metriq.app.voice

import com.metriq.data.db.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodPhraseParserTest {

    private fun food(name: String, kcal: Double = 100.0) = Food(
        id = 1L,
        source = "test",
        sourceId = name,
        barcode = null,
        name = name,
        brand = null,
        category = null,
        iconId = null,
        calories = kcal,
        protein = 2.0,
        fat = 1.0,
        carbs = 20.0,
        fiber = null,
        sugar = null,
        sodium = null,
        servingLabel = null,
        servingGrams = null,
        verified = 0L,
        isCustom = 0L,
    )

    private val catalog = listOf(
        food("Arroz, cozido"),
        food("Banana"),
        food("Chicken breast, cooked"),
    )

    @Test
    fun `parses portuguese spoon of rice`() {
        val parsed = FoodPhraseParser.parse("uma colher de sopa de arroz", catalog)
        assertNotNull(parsed)
        assertEquals(15.0, parsed!!.grams, 0.001)
    }

    @Test
    fun `parses english numeric quantity and unit`() {
        val parsed = FoodPhraseParser.parse("2 cups of cooked rice", catalog)
        assertNotNull(parsed)
        assertEquals(480.0, parsed!!.grams, 0.001)
    }

    @Test
    fun `name only defaults to one hundred grams`() {
        val parsed = FoodPhraseParser.parse("banana", catalog)
        assertNotNull(parsed)
        assertEquals(100.0, parsed!!.grams, 0.001)
    }

    @Test
    fun `parses decimal comma`() {
        val parsed = FoodPhraseParser.parse("2,5 fatias de banana", catalog)
        assertNotNull(parsed)
        assertEquals(75.0, parsed!!.grams, 0.001)
    }

    @Test
    fun `returns null when no food resolves`() {
        assertNull(FoodPhraseParser.parse("uma colher de sopa de inexistente", catalog))
    }
}
