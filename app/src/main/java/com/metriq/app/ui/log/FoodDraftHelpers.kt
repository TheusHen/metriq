package com.metriq.app.ui.log

import com.metriq.data.FoodHit
import com.metriq.data.db.Food

/**
 * Builds a [FoodDraft] from a catalog hit without pre-multiplying macros.
 *
 * LogRepository scales stored macros by `grams / 100` when it reads a log entry, so the
 * draft must keep the raw per-100 g values and only record the chosen serving size. Doing
 * the multiplication here too would log 200 g of 60 kcal/100 g milk as 240 kcal.
 */
internal fun FoodHit.toFoodDraft(grams: Double, defaultServingLabel: String): FoodDraft = FoodDraft(
    name = name,
    brand = brand,
    calories = calories,
    protein = protein,
    fat = fat,
    carbs = carbs,
    servingLabel = servingLabel ?: defaultServingLabel,
    servingGrams = grams,
)

/** Same raw-macro contract for a locally stored catalog row (voice parsing path). */
internal fun Food.toFoodDraft(grams: Double, defaultServingLabel: String): FoodDraft = FoodDraft(
    name = name,
    brand = brand,
    calories = calories,
    protein = protein,
    fat = fat,
    carbs = carbs,
    servingLabel = servingLabel ?: defaultServingLabel,
    servingGrams = grams,
)

/** Serving size used by a catalog hit, with a safe fallback for missing or invalid values. */
internal fun FoodHit.effectiveGrams(): Double =
    servingGrams?.takeIf { it.isFinite() && it > 0.0 } ?: 100.0

/** Calories shown next to a multi-select of catalog hits, scaled to each hit's serving. */
internal fun Collection<FoodHit>.previewKcal(): Int =
    sumOf { it.calories * it.effectiveGrams() / 100.0 }.toInt()
