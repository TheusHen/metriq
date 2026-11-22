package com.metriq.app.ui.log

data class FoodDraft(
    val name: String,
    val brand: String?,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingLabel: String,
    val servingGrams: Double,
)
