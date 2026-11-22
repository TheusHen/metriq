package com.metriq.core

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    HIGH(1.725),
    ATHLETE(1.9),
}

enum class GoalType { CUT, MAINTAIN, BULK }

enum class Aggressiveness { CONSERVATIVE, MODERATE, AGGRESSIVE }

enum class DietStyle(val fatShare: Double) {
    BALANCED(0.30),
    LOW_CARB_HIGH_FAT(0.45),
    HIGH_CARB_LOW_FAT(0.20),
    KETO(0.70),
}

enum class ProteinTier { LOW, MODERATE, HIGH, EXTRA }

data class Profile(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: GoalType = GoalType.MAINTAIN,
    val diet: DietStyle = DietStyle.BALANCED,
    val proteinTier: ProteinTier = ProteinTier.MODERATE,
) {
    val fatMassKg: Double?
        get() = bodyFatPercent?.let { weightKg * it / 100.0 }

    val fatFreeMassKg: Double?
        get() = fatMassKg?.let { weightKg - it }
}

data class WeightEntry(val date: LocalDate, val kg: Double)
