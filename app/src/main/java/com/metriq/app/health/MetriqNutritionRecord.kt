package com.metriq.app.health

import com.metriq.data.LogItem

/** Coarse meal bucket derived from a log's hour. Pure: no Android or Health Connect types. */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Maps a Metriq log hour (0..23) onto a meal bucket. Anything outside the usual eating
 * windows — including out-of-range values — is treated as a snack rather than dropped.
 */
fun mealSlotForHour(hour: Int): MealSlot = when (hour) {
    in 5..10 -> MealSlot.BREAKFAST
    in 11..15 -> MealSlot.LUNCH
    in 16..20 -> MealSlot.DINNER
    else -> MealSlot.SNACK
}

/**
 * One food log as exported to Health Connect. Mirrors the fields [LogItem] exposes so the
 * worker can build a NutritionRecord without reaching into core-data query types.
 */
data class MetriqNutritionRecord(
    val id: Long,
    val date: String,
    val hour: Int,
    val foodId: Long,
    val name: String,
    val brand: String?,
    val iconId: String?,
    val grams: Double,
    val servings: Double,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
) {
    companion object {
        /**
         * Deterministic client record id: re-exporting the same log updates the existing
         * Health Connect record instead of creating a duplicate.
         */
        fun clientRecordId(logId: Long): String = "metriq-log-$logId"

        fun from(log: LogItem): MetriqNutritionRecord = MetriqNutritionRecord(
            id = log.id,
            date = log.date,
            hour = log.hour,
            foodId = log.foodId,
            name = log.name,
            brand = log.brand,
            iconId = log.iconId,
            grams = log.grams,
            servings = log.servings,
            calories = log.calories,
            protein = log.protein,
            fat = log.fat,
            carbs = log.carbs,
        )
    }
}
