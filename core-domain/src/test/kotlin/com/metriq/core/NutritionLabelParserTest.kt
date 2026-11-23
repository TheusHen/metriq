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
    fun `thousand separators are stripped before decimal normalization`() {
        val text = """
            Nutrition Facts
            Total Fat 1.000 g
            Sodium 1,000 mg
            Total Sugars 3,5 g
            Protein 9 g
        """.trimIndent()
        val label = parser.parse(text)
        assertEquals(1000.0, label.totalFat!!)
        assertEquals(1000.0, label.sodium!!)
        assertEquals(3.5, label.sugars!!)
        assertEquals(9.0, label.protein!!)
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

    @Test
    fun `parses a brazilian table with row labels and serving info`() {
        val text = """
            INFORMAÇÃO NUTRICIONAL
            Porção 100 g
            Valor energético 230 kcal
            Gorduras totais 8g
            Carboidratos 30g
            Proteínas 5g
            Sódio 400mg
        """.trimIndent()
        val label = parser.parse(text)

        assertEquals("100 g", label.servingSize)
        assertEquals(230.0, label.calories!!, 0.001)
        assertEquals(8.0, label.totalFat!!, 0.001)
        assertEquals(30.0, label.totalCarbs!!, 0.001)
        assertEquals(5.0, label.protein!!, 0.001)
        assertEquals(400.0, label.sodium!!, 0.001)
        assertEquals(EnergySource.KCAL, label.energySource)
    }

    @Test
    fun `explicit kcal wins when a dual kj kcal label is present`() {
        val label = parser.parse("Energia 1000 kJ / 239 kcal\nGorduras totais 8g")

        assertEquals(239.0, label.calories!!, 0.001)
        assertEquals(EnergySource.KCAL, label.energySource)
    }

    @Test
    fun `kj only energy is converted and marked in the result`() {
        val label = parser.parse("Energia 1000 kJ\nProteínas 5g")

        assertEquals(1000.0 / 4.184, label.calories!!, 0.001)
        assertEquals(EnergySource.KJ_CONVERTED, label.energySource)
        assertEquals(5.0, label.protein!!, 0.001)
    }

    @Test
    fun `values on the line after the label survive ocr noise`() {
        val text = """
            Valor energético
              · 230 kcal
            Gorduras totais
              · 8 g
            Carboidratos
              · 30 g
            Proteínas
              · 5 g
        """.trimIndent()
        val label = parser.parse(text)

        assertEquals(230.0, label.calories!!, 0.001)
        assertEquals(8.0, label.totalFat!!, 0.001)
        assertEquals(30.0, label.totalCarbs!!, 0.001)
        assertEquals(5.0, label.protein!!, 0.001)
    }

    @Test
    fun `parses a us label with thousands separated calories and sodium`() {
        val text = """
            Nutrition Facts
            Serving Size: 1 package
            Calories 2,000
            Total Fat 100 g
            Sodium 1,000 mg
            Total Carbohydrate 300 g
            Protein 20 g
        """.trimIndent()
        val label = parser.parse(text)

        assertEquals("1 package", label.servingSize)
        assertEquals(2000.0, label.calories!!, 0.001)
        assertEquals(1000.0, label.sodium!!, 0.001)
        assertEquals(20.0, label.protein!!, 0.001)
        assertEquals(EnergySource.KCAL, label.energySource)
    }

    @Test
    fun `energy keyword without a unit is treated as kcal`() {
        val label = parser.parse("Energia 180\nProteínas 4g")

        assertEquals(180.0, label.calories!!, 0.001)
        assertEquals(EnergySource.KCAL, label.energySource)
    }

    @Test
    fun `kj with a thousands separator converts to one thousand kcal`() {
        val label = parser.parse("Energia 4.184 kJ")

        assertEquals(1000.0, label.calories!!, 0.001)
        assertEquals(EnergySource.KJ_CONVERTED, label.energySource)
    }

    @Test
    fun `energy source defaults to kcal when no energy value is found`() {
        val label = parser.parse("Total Fat 8g")

        assertEquals(null, label.calories)
        assertEquals(EnergySource.KCAL, label.energySource)
    }
}
