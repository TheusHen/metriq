package com.metriq.app.log

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.metriq.data.FoodHit
import com.metriq.data.MetriqStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FoodRecognitionManager(private val store: MetriqStore) {

    data class Suggestion(
        val label: String,
        val confidence: Float,
        val food: FoodHit?,
    )

    /** Offloaded to Dispatchers.Default: ML Kit inference and matching can be slow. */
    suspend fun recognize(bitmap: Bitmap): List<Suggestion> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            .process(image)
            .await()
        if (labels.isEmpty()) return@withContext emptyList()

        val foods = store.food.recent(5000)
        labels.map { label ->
            val term = label.text.lowercase().trim()
            val matches = foods.filter { food ->
                food.name.lowercase().contains(term) || term.contains(food.name.lowercase().take(6))
            }
            Suggestion(
                label = label.text.replaceFirstChar { it.uppercase() },
                confidence = label.confidence,
                food = matches.firstOrNull()?.let { food ->
                    FoodHit(
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
                },
            )
        }.sortedByDescending { it.confidence }.take(8)
    }
}
