package com.metriq.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutritionLabelParserTest {

    private val parser = NutritionLabelParser()

    @Test
    fun `parses english label`() {
        val text = """
            Nutrition Facts
            Serving Size: 1 cup (40 g)
            Calories 230
            Total Fat 8g
            Saturated Fat 2g
            Sodium 320mg
            Total Carbohydrate 31g
            Dietary Fiber 5g
            Total Sugars 12g
            Protein 9g
        """.trimIndent()
        val label = parser.parse(text)
        assertEquals(230.0, label.calories!!)
        assertEquals(8.0, label.totalFat!!)
        assertEquals(320.0, label.sodium!!)
        assertEquals(9.0, label.protein!!)
        assertEquals(1f, label.confidence)
    }

    @Test
    fun `parses portuguese label with comma decimals`() {
        val text = """
            Informação Nutricional
            Porção: 30 g
            Valor energético 150 kcal
            Gorduras totais 3,5 g
            Gorduras saturadas 1 g
            Sódio 120 mg
            Carboidratos totais 25 g
            Fibra alimentar 4 g
            Açúcares 10 g
            Proteínas 6 g
        """.trimIndent()
        val label = parser.parse(text)
        assertEquals(150.0, label.calories!!)
        assertEquals(3.5, label.totalFat!!)
        assertEquals(6.0, label.protein!!)
    }

    @Test
    fun `low confidence on irrelevant text`() {
        val label = parser.parse("Just a shopping list with milk and bread.")
        assertTrue(label.confidence < 0.2f)
    }

    @Test
    fun `calories from fat line is not treated as total calories`() {
        val label = parser.parse("Calories from Fat 120\nTotal Fat 13g")
        assertEquals(null, label.calories)
        assertEquals(13.0, label.totalFat!!)
    }

    @Test
    fun `serving size without a colon is parsed without the label`() {
        val label = parser.parse("Serving Size 1 cup (40 g)\nCalories 200")
        assertEquals("1 cup (40 g)", label.servingSize)
        assertEquals(200.0, label.calories!!)
    }

    @Test
    fun `total calories still wins when a calories from fat line is also present`() {
        val text = """
            Nutrition Facts
            Calories 230
            Calories from Fat 120
            Total Fat 13g
        """.trimIndent()
        val label = parser.parse(text)
        assertEquals(230.0, label.calories!!)
    }
}
