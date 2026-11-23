package com.metriq.core

/** Parses nutrition labels from English and Portuguese text. Values are per serving. */
class NutritionLabelParser {

    data class ParsedLabel(
        val servingSize: String? = null,
        val calories: Double? = null,
        val totalFat: Double? = null,
        val saturatedFat: Double? = null,
        val sodium: Double? = null,
        val totalCarbs: Double? = null,
        val fiber: Double? = null,
        val sugars: Double? = null,
        val protein: Double? = null,
        val confidence: Float = 0f,
    )

    fun parse(raw: String): ParsedLabel {
        // A comma/dot followed by exactly three digits is a thousands separator,
        // not a decimal point ("1,000" / "1.000" both mean one thousand).
        val withoutThousands = Regex("(?<=\\d)[,.](\\d{3})(?=\\D|$)").replace(raw) { it.groupValues[1] }
        // "Calories from Fat" is a breakdown line, not the total calories for the serving.
        val text = withoutThousands.replace(',', '.')
            .replace(
                Regex(".*\\bcalories\\s+from\\s+fat\\b.*", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
                "",
            )
        fun grab(vararg keys: String): Double? {
            keys.forEach { key ->
                val regex = Regex("$key[^0-9.\\n]{0,30}([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                regex.find(text)?.let { return it.groupValues[1].toDoubleOrNull() }
            }
            return null
        }

        val calories = grab("calories", "valor energético", "energia")
        val fat = grab("total fat", "gorduras totais", "gordura total")
        val saturated = grab("saturated fat", "gorduras saturadas")
        val sodium = grab("sodium", "sódio")
        val carbs = grab("total carbohydrate", "total carbs", "carboidratos totais")
        val fiber = grab("dietary fiber", "fiber", "fibra alimentar", "fibras")
        val sugars = grab("total sugars", "sugars", "açúcares totais", "açúcares")
        val protein = grab("protein", "proteínas", "proteina")

        val found = listOf(calories, fat, saturated, sodium, carbs, fiber, sugars, protein)
            .count { it != null }

        return ParsedLabel(
            servingSize = Regex("(?:serving size|porção)\\s*:?\\s*([^\\n]{1,40})", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim()?.ifBlank { null },
            calories = calories,
            totalFat = fat,
            saturatedFat = saturated,
            sodium = sodium,
            totalCarbs = carbs,
            fiber = fiber,
            sugars = sugars,
            protein = protein,
            confidence = found / 8f,
        )
    }
}
