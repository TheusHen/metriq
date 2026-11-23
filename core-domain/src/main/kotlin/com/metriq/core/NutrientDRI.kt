package com.metriq.core

/** Reference daily intakes (adults). Values are per day in the unit named in the key. */
object NutrientDRI {

    val NUTRIENTS = listOf(
        NutrientDef("fiber", "Fiber", "g", 28.0),
        NutrientDef("sodium", "Sodium", "mg", 2300.0),
        NutrientDef("potassium", "Potassium", "mg", 4700.0),
        NutrientDef("calcium", "Calcium", "mg", 1000.0),
        NutrientDef("iron", "Iron", "mg", 18.0),
        NutrientDef("magnesium", "Magnesium", "mg", 420.0),
        NutrientDef("phosphorus", "Phosphorus", "mg", 1250.0),
        NutrientDef("zinc", "Zinc", "mg", 11.0),
        NutrientDef("vitaminA", "Vitamin A", "mcg", 900.0),
        NutrientDef("vitaminC", "Vitamin C", "mg", 90.0),
        NutrientDef("vitaminD", "Vitamin D", "mcg", 20.0),
        NutrientDef("vitaminE", "Vitamin E", "mg", 15.0),
        NutrientDef("vitaminK", "Vitamin K", "mcg", 120.0),
        NutrientDef("vitaminB1", "Vitamin B1", "mg", 1.2),
        NutrientDef("vitaminB2", "Vitamin B2", "mg", 1.3),
        NutrientDef("vitaminB3", "Vitamin B3", "mg", 16.0),
        NutrientDef("vitaminB6", "Vitamin B6", "mg", 1.7),
        NutrientDef("vitaminB9", "Folate", "mcg", 400.0),
        NutrientDef("vitaminB12", "Vitamin B12", "mcg", 2.4),
        NutrientDef("cholesterol", "Cholesterol", "mg", 300.0),
        NutrientDef("omega3", "Omega 3", "g", 1.6),
        NutrientDef("caffeine", "Caffeine", "mg", 400.0),
        NutrientDef("water", "Water", "ml", 2500.0),
    )

    /** Adult female RDAs that differ from the male/default list above. */
    private val FEMALE_VALUES = mapOf(
        "fiber" to 25.0,
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

    fun value(key: String, sex: Sex = Sex.MALE, age: Int = 30): Double {
        val base = NUTRIENTS.firstOrNull { it.key == key }?.adultValue ?: return 0.0
        // Iron keeps its age-dependent rule: 18 mg for women under 51, 8 mg otherwise.
        if (key == "iron") return if (sex == Sex.FEMALE && age < 51) 18.0 else 8.0
        return if (sex == Sex.FEMALE) FEMALE_VALUES[key] ?: base else base
    }
}

data class NutrientDef(
    val key: String,
    val label: String,
    val unit: String,
    val adultValue: Double,
)
