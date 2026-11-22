package com.metriq.app.log

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.metriq.data.FoodHit
import com.metriq.data.MetriqStore
import com.metriq.data.db.Food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

/**
 * Fully on-device recognition: ML Kit image labels plus OCR text (packaging words) are
 * matched against the local food library. No image or text ever leaves the device.
 */
class FoodRecognitionManager(private val store: MetriqStore) {

    data class Suggestion(
        val label: String,
        val confidence: Float,
        val food: FoodHit?,
    )

    /** Offloaded to Dispatchers.Default: inference and matching must not block the UI. */
    suspend fun recognize(bitmap: Bitmap): List<Suggestion> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = runCatching {
            val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            try {
                client.process(image).await()
            } finally {
                client.close()
            }
        }.getOrElse { emptyList<ImageLabel>() }

        val text = runCatching {
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                client.process(image).await()
            } finally {
                client.close()
            }
        }.getOrNull()

        // Display text -> model confidence. OCR has no confidence, so it contributes neutrally.
        val sources = LinkedHashMap<String, Float>()
        labels.take(10).forEach { label ->
            val display = label.text.trim()
            if (display.isNotEmpty()) sources.putIfAbsent(display, label.confidence)
        }
        text?.textBlocks
            ?.flatMap { it.lines }
            ?.map { it.text.trim() }
            ?.filter { it.length in 2..80 && normalize(it).any(Char::isLetter) }
            ?.take(12)
            ?.forEach { line -> sources.putIfAbsent(line, 1f) }

        if (sources.isEmpty()) return@withContext emptyList()

        // One snapshot of the library; both LIKE search and token overlap use it.
        val recent = store.food.recent(5000)
        val index: List<Pair<FoodHit, Set<String>>> = recent.map { food ->
            toHit(food) to tokenSet("${food.name} ${food.brand.orEmpty()} ${food.category.orEmpty()}")
        }
        val hitById = HashMap<Long, FoodHit>().apply {
            index.forEach { put(it.first.id, it.first) }
        }

        val found = ArrayList<Suggestion>()
        for ((source, modelConfidence) in sources) {
            val normalized = normalize(source)
            val tokens = tokenSet(normalized)
            if (tokens.isEmpty()) continue

            // Full phrase first, then individual words; cap queries so a noisy OCR line is cheap.
            val queries = (listOf(normalized) + tokens)
                .distinct()
                .filter { it.length >= 3 }
                .take(4)
            val scores = HashMap<Long, Double>()

            for (term in queries) {
                store.food.search(term, limit = 12).forEachIndexed { position, hit ->
                    hitById.putIfAbsent(hit.id, hit)
                    val rank = 1.0 / (1.0 + position * 0.35)
                    val quality = searchQuality(hit, term)
                    scores[hit.id] = maxOf(scores[hit.id] ?: 0.0, quality * rank)
                }
            }

            // Token overlap catches names OCR reads as a phrase the LIKE search cannot match
            // exactly (e.g. "Coca Cola Classic" vs "Coca-Cola").
            for ((hit, foodTokens) in index) {
                if (foodTokens.isEmpty()) continue
                val overlap = tokens.count { it in foodTokens }
                if (overlap == 0) continue
                val containment = overlap.toDouble() / minOf(tokens.size, foodTokens.size)
                val jaccard = overlap.toDouble() / (tokens.size + foodTokens.size - overlap)
                val score = maxOf(containment, jaccard) * 0.92
                if (score >= MIN_OVERLAP) scores[hit.id] = maxOf(scores[hit.id] ?: 0.0, score)
            }

            val best = scores.maxByOrNull { it.value }?.takeIf { it.value >= MIN_MATCH } ?: continue
            val hit = hitById[best.key] ?: continue
            val confidence = (0.82 * best.value + 0.18 * modelConfidence.toDouble())
                .coerceIn(0.0, 1.0).toFloat()
            found += Suggestion(
                label = source.replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
                },
                confidence = confidence,
                food = hit,
            )
        }

        found
            .sortedByDescending { it.confidence }
            .distinctBy { it.food?.id }
            .take(MAX_SUGGESTIONS)
    }

    private companion object {
        const val MIN_MATCH = 0.55
        const val MIN_OVERLAP = 0.60
        const val MAX_SUGGESTIONS = 8

        val STOPWORDS = setOf(
            "the", "and", "with", "for", "from", "this", "that", "have", "has",
            "are", "was", "were", "your", "new", "fresh", "food", "nutrition",
            "serving", "ingredients", "calorie", "calories", "kcal", "total",
            "net", "wt", "per",
        )
    }

    /** Lowercase, strip accents and punctuation, collapse whitespace. */
    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokenSet(normalized: String): Set<String> = normalized
        .split(' ')
        .asSequence()
        .map(String::trim)
        .filter { it.length >= 3 && it !in STOPWORDS && it.any(Char::isLetter) }
        .toSet()

    /** Discount generic substring hits so a real name match outranks a category guess. */
    private fun searchQuality(hit: FoodHit, term: String): Double {
        val name = normalize(hit.name)
        val brand = normalize(hit.brand.orEmpty())
        val category = normalize(hit.category.orEmpty())
        return when {
            name == term -> 1.0
            name.startsWith(term) -> 0.88
            name.split(' ').any { it == term } -> 0.82
            name.contains(term) -> 0.74
            brand == term || brand.split(' ').any { it == term } -> 0.66
            brand.contains(term) || category.contains(term) -> 0.58
            else -> 0.5
        }
    }

    private fun toHit(food: Food) = FoodHit(
        id = food.id,
        name = food.name,
        brand = food.brand,
        category = food.category,
        iconId = food.iconId,
        calories = food.calories,
        protein = food.protein,
        fat = food.fat,
        carbs = food.carbs,
        servingLabel = food.servingLabel,
        servingGrams = food.servingGrams,
    )
}
