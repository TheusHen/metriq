package com.metriq.app.voice

import com.metriq.data.db.Food
import java.text.Normalizer
import java.util.Locale

/** A food portion resolved from a spoken/typed phrase. Values are expressed in grams. */
data class ParsedPortion(
    val foodName: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val confidence: Float,
)

/**
 * Pure, side-effect-free parser for short food phrases in Portuguese, English and Spanish.
 * It understands number words/digits, common kitchen units and resolves the remaining text
 * against a supplied [Food] catalog by normalized substring. Returns null when no food matches.
 */
object FoodPhraseParser {

    /** Grams assumed for one implicit item/serving when no explicit unit is present. */
    private const val DEFAULT_GRAMS = 100.0

    private val NUMBER_WORDS = mapOf(
        // English
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0, "half" to 0.5, "quarter" to 0.25,
        "couple" to 2.0, "dozen" to 12.0,
        // Portuguese
        "um" to 1.0, "uma" to 1.0, "dois" to 2.0, "duas" to 2.0, "tres" to 3.0,
        "quatro" to 4.0, "cinco" to 5.0, "seis" to 6.0, "sete" to 7.0, "oito" to 8.0,
        "nove" to 9.0, "dez" to 10.0, "meia" to 0.5, "metade" to 0.5,
        // Spanish
        "un" to 1.0, "una" to 1.0, "dos" to 2.0, "cuatro" to 4.0, "siete" to 7.0,
        "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "media" to 0.5, "mitad" to 0.5,
    )

    /** Filler words removed before matching the food name. */
    private val STOP_WORDS = setOf(
        "de", "da", "do", "das", "dos", "e", "y", "com", "with", "of", "the",
        "o", "os", "el", "la", "las", "los", "an", "a", "um", "uma",
        "pouco", "pouca", "little", "bit", "some", "algo", "about", "aprox", "approx",
    )

    private data class UnitDef(
        val tokens: List<String>,
        val gramsPerUnit: Double,
        val label: String,
    )

    /** Longest (multi-token) units first so "colher de sopa" wins over a shorter match. */
    private val UNITS = listOf(
        UnitDef(listOf("colher", "de", "sopa"), 15.0, "colher de sopa"),
        UnitDef(listOf("colheres", "de", "sopa"), 15.0, "colher de sopa"),
        UnitDef(listOf("colher", "de", "cha"), 5.0, "colher de cha"),
        UnitDef(listOf("colheres", "de", "cha"), 5.0, "colher de cha"),
        UnitDef(listOf("cucharada"), 15.0, "colher de sopa"),
        UnitDef(listOf("cucharadita"), 5.0, "colher de cha"),
        UnitDef(listOf("tablespoon"), 15.0, "colher de sopa"),
        UnitDef(listOf("tablespoons"), 15.0, "colher de sopa"),
        UnitDef(listOf("tbsp"), 15.0, "colher de sopa"),
        UnitDef(listOf("teaspoon"), 5.0, "colher de cha"),
        UnitDef(listOf("teaspoons"), 5.0, "colher de cha"),
        UnitDef(listOf("tsp"), 5.0, "colher de cha"),
        UnitDef(listOf("xicara"), 240.0, "xicara"),
        UnitDef(listOf("xicaras"), 240.0, "xicara"),
        UnitDef(listOf("taza"), 240.0, "xicara"),
        UnitDef(listOf("tazas"), 240.0, "xicara"),
        UnitDef(listOf("cup"), 240.0, "xicara"),
        UnitDef(listOf("cups"), 240.0, "xicara"),
        UnitDef(listOf("copo"), 200.0, "copo"),
        UnitDef(listOf("copos"), 200.0, "copo"),
        UnitDef(listOf("glass"), 200.0, "copo"),
        UnitDef(listOf("glasses"), 200.0, "copo"),
        UnitDef(listOf("vaso"), 200.0, "copo"),
        UnitDef(listOf("vasos"), 200.0, "copo"),
        UnitDef(listOf("quilograma"), 1000.0, "kg"),
        UnitDef(listOf("quilogramas"), 1000.0, "kg"),
        UnitDef(listOf("kilogram"), 1000.0, "kg"),
        UnitDef(listOf("kilograms"), 1000.0, "kg"),
        UnitDef(listOf("quilo"), 1000.0, "kg"),
        UnitDef(listOf("quilos"), 1000.0, "kg"),
        UnitDef(listOf("kgs"), 1000.0, "kg"),
        UnitDef(listOf("kg"), 1000.0, "kg"),
        UnitDef(listOf("gramas"), 1.0, "g"),
        UnitDef(listOf("grama"), 1.0, "g"),
        UnitDef(listOf("grams"), 1.0, "g"),
        UnitDef(listOf("gram"), 1.0, "g"),
        UnitDef(listOf("mililitros"), 1.0, "ml"),
        UnitDef(listOf("mililitro"), 1.0, "ml"),
        UnitDef(listOf("ml"), 1.0, "ml"),
        UnitDef(listOf("g"), 1.0, "g"),
        UnitDef(listOf("fatia"), 30.0, "fatia"),
        UnitDef(listOf("fatias"), 30.0, "fatia"),
        UnitDef(listOf("slice"), 30.0, "fatia"),
        UnitDef(listOf("slices"), 30.0, "fatia"),
        UnitDef(listOf("punhado"), 30.0, "punhado"),
        UnitDef(listOf("punhados"), 30.0, "punhado"),
        UnitDef(listOf("handful"), 30.0, "punhado"),
        UnitDef(listOf("handfuls"), 30.0, "punhado"),
        UnitDef(listOf("unidade"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("unidades"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("unidad"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("unit"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("units"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("piece"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("pieces"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("peca"), DEFAULT_GRAMS, "unidade"),
        UnitDef(listOf("pecas"), DEFAULT_GRAMS, "unidade"),
    ).sortedByDescending { it.tokens.size }

    private val FRACTION = Regex("^(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)$")

    fun parse(phrase: String, catalog: List<Food>): ParsedPortion? {
        val normalized = normalize(phrase)
        if (normalized.isBlank() || catalog.isEmpty()) return null
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // 1. Quantity: first number word or digit/fraction in the phrase.
        var quantity = 1.0
        var quantityIndex = -1
        for (i in tokens.indices) {
            val value = numberValue(tokens[i])
            if (value != null && value > 0.0) {
                quantity = value
                quantityIndex = i
                break
            }
        }

        // 2. Unit: longest matching unit phrase after the quantity.
        val consumed = HashSet<Int>()
        if (quantityIndex >= 0) consumed.add(quantityIndex)
        var unitGrams = DEFAULT_GRAMS
        var unitLabel = "unidade"
        var unitMatch: Pair<Int, UnitDef>? = null
        for (i in tokens.indices) {
            if (i in consumed) continue
            for (unit in UNITS) {
                if (i + unit.tokens.size <= tokens.size &&
                    unit.tokens.indices.all { offset -> tokens[i + offset] == unit.tokens[offset] }
                ) {
                    unitMatch = i to unit
                    break
                }
            }
            if (unitMatch != null) break
        }
        if (unitMatch != null) {
            val (start, unit) = unitMatch
            for (i in start until start + unit.tokens.size) consumed.add(i)
            unitGrams = unit.gramsPerUnit
            unitLabel = unit.label
        }

        val grams = quantity * unitGrams
        if (grams <= 0.0 || grams.isNaN() || grams.isInfinite()) return null

        // 3. Food name: whatever is left once quantity, unit and filler are removed.
        val candidate = tokens
            .filterIndexed { index, _ -> index !in consumed }
            .filterNot { it in STOP_WORDS }
            .joinToString(" ")
            .trim()
        if (candidate.length < 2) return null

        // 4. Resolve against the catalog by normalized substring / token overlap.
        // A zero score means no relation at all — it must never select a food just
        // because its name happens to be longer than the previous candidate.
        var bestFood: Food? = null
        var bestScore = 0.0
        var bestLength = 0
        for (food in catalog) {
            val foodName = normalize(food.name)
            val score = scoreMatch(foodName, candidate)
            if (score > 0.0 && (score > bestScore || (score == bestScore && foodName.length > bestLength))) {
                bestFood = food
                bestScore = score
                bestLength = foodName.length
            }
        }
        val food = bestFood ?: return null

        val confidence = when {
            bestScore >= 100.0 -> 1.0f
            bestScore >= 80.0 -> 0.92f
            bestScore >= 60.0 -> 0.82f
            bestScore >= 40.0 -> 0.70f
            else -> 0.55f
        }
        return ParsedPortion(
            foodName = food.name,
            quantity = quantity,
            unit = unitLabel,
            grams = grams,
            confidence = confidence,
        )
    }

    private fun normalize(raw: String): String {
        val plain = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return plain
            .replace(Regex("[^\\p{L}\\p{N}.,/]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun numberValue(token: String): Double? {
        NUMBER_WORDS[token]?.let { return it }
        val clean = token.replace(',', '.')
        FRACTION.matchEntire(clean)?.let { match ->
            val denominator = match.groupValues[2].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            return match.groupValues[1].toDoubleOrNull()?.div(denominator)
        }
        return clean.toDoubleOrNull()
    }

    private fun scoreMatch(foodName: String, candidate: String): Double {
        if (foodName.isBlank() || candidate.isBlank()) return 0.0
        if (foodName == candidate) return 100.0
        if (foodName.contains(candidate)) return 90.0 + candidate.length.coerceAtMost(30) / 100.0
        if (candidate.contains(foodName)) return 80.0 + foodName.length.coerceAtMost(30) / 100.0

        val candidateTokens = candidate.split(' ')
        val foodTokens = foodName.split(' ')
        if (candidateTokens.size <= foodTokens.size && containsSlice(foodTokens, candidateTokens)) {
            return 70.0 + candidateTokens.size
        }
        if (foodTokens.size <= candidateTokens.size && containsSlice(candidateTokens, foodTokens)) {
            return 60.0 + foodTokens.size
        }
        val overlap = candidateTokens.count { it.length >= 3 && it in foodTokens }
        return if (overlap > 0) 30.0 + overlap else 0.0
    }

    private fun containsSlice(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { start ->
            needle.indices.all { offset -> haystack[start + offset] == needle[offset] }
        }
    }
}
