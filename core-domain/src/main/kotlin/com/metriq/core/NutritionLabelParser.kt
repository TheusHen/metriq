package com.metriq.core

/** Where the parsed energy value came from. */
enum class EnergySource {
    /** A `kcal`/`Calories` value was printed (or implied) on the label. */
    KCAL,
    /** Only `kJ` was printed; calories were derived by dividing by 4.184. */
    KJ_CONVERTED,
}

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
        /** Defaults to KCAL so existing callers keep compiling unchanged. */
        val energySource: EnergySource = EnergySource.KCAL,
    )

    companion object {
        private const val KJ_PER_KCAL = 4.184
    }

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
                // Same line: the label, up to 30 chars of OCR noise, then the number.
                val sameLine = Regex(
                    "$key[^0-9\\n]{0,30}([0-9]+(?:\\.[0-9]+)?)",
                    RegexOption.IGNORE_CASE,
                )
                sameLine.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }

                // Next line: the label has no number, and the following line is a bare
                // value with only whitespace/punctuation before it. Requiring no letters
                // before the number prevents a following nutrient label from being read
                // as this nutrient's value.
                val nextLine = Regex(
                    "$key[^0-9\\n]{0,30}[ \\t]*\\r?\\n[^\\d\\p{L}\\n]{0,12}([0-9]+(?:\\.[0-9]+)?)",
                    RegexOption.IGNORE_CASE,
                )
                nextLine.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
            }
            return null
        }

        val explicitKcal = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*kcal", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        val plainCalories = if (explicitKcal == null) grab("calories") else null

        val kilojoules = if (explicitKcal == null && plainCalories == null) {
            Regex("([0-9]+(?:\\.[0-9]+)?)\\s*kj", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        } else {
            null
        }

        // Prefer an explicit kcal number; kJ is only converted when no kcal is present.
        val energy: Pair<Double?, EnergySource> = when {
            explicitKcal != null -> explicitKcal to EnergySource.KCAL
            plainCalories != null -> plainCalories to EnergySource.KCAL
            kilojoules != null -> (kilojoules / KJ_PER_KCAL) to EnergySource.KJ_CONVERTED
            else -> grab("valor energético", "energia", "energy")?.let { it to EnergySource.KCAL }
                ?: (null to EnergySource.KCAL)
        }

        val fat = grab("total fat", "gorduras totais", "gordura total")
        val saturated = grab("saturated fat", "gorduras saturadas")
        val sodium = grab("sodium", "sódio", "sodio")
        val carbs = grab("total carbohydrate", "total carbs", "carboidratos totais", "carboidratos", "carbohidratos")
        val fiber = grab("dietary fiber", "fiber", "fibra alimentar", "fibras")
        val sugars = grab("total sugars", "sugars", "açúcares totais", "açúcares", "acucares")
        val protein = grab("protein", "proteínas", "proteina")

        val found = listOf(energy.first, fat, saturated, sodium, carbs, fiber, sugars, protein)
            .count { it != null }

        return ParsedLabel(
            servingSize = Regex("(?:serving size|porção)\\s*:?\\s*([^\\n]{1,40})", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim()?.ifBlank { null },
            calories = energy.first,
            totalFat = fat,
            saturatedFat = saturated,
            sodium = sodium,
            totalCarbs = carbs,
            fiber = fiber,
            sugars = sugars,
            protein = protein,
            confidence = found / 8f,
            energySource = energy.second,
        )
    }
}
