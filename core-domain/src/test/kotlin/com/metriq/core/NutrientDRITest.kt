package com.metriq.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NutrientDRITest {

    @Test
    fun `female values use the adult female reference intakes`() {
        val expected = mapOf(
            "fiber" to 25.0,
            "iron" to 18.0,
            "vitaminA" to 700.0,
            "vitaminC" to 75.0,
            "vitaminE" to 15.0,
            "vitaminK" to 90.0,
            "magnesium" to 310.0,
            "zinc" to 8.0,
            "vitaminB1" to 1.1,
            "vitaminB2" to 1.1,
            "vitaminB3" to 14.0,
            "vitaminB6" to 1.3,
            "vitaminB9" to 400.0,
            "vitaminB12" to 2.4,
        )
        expected.forEach { (key, value) ->
            assertEquals(value, NutrientDRI.value(key, Sex.FEMALE, 30), 0.0001, key)
        }
        // Iron drops to the adult value after 50.
        assertEquals(8.0, NutrientDRI.value("iron", Sex.FEMALE, 51), 0.0001)
    }

    @Test
    fun `male and default values are unchanged`() {
        val expected = mapOf(
            "fiber" to 28.0,
            "iron" to 8.0,
            "vitaminA" to 900.0,
            "vitaminC" to 90.0,
            "vitaminE" to 15.0,
            "vitaminK" to 120.0,
            "magnesium" to 420.0,
            "zinc" to 11.0,
            "vitaminB1" to 1.2,
            "vitaminB2" to 1.3,
            "vitaminB3" to 16.0,
            "vitaminB6" to 1.7,
            "vitaminB9" to 400.0,
            "vitaminB12" to 2.4,
        )
        expected.forEach { (key, value) ->
            assertEquals(value, NutrientDRI.value(key), 0.0001, key)
            assertEquals(value, NutrientDRI.value(key, Sex.MALE, 30), 0.0001, key)
        }
    }
}
