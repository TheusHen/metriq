package com.metriq.core

data class MacroTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbG: Int,
)

data class CalorieTargets(
    val dailyKcal: Double,
    val weeklyKcal: Double,
)

object CaloriePlan {

    private const val MAINTAIN_TOLERANCE_KG = 0.68

    fun target(
        expenditure: Double,
        goal: GoalType,
        aggressiveness: Aggressiveness = Aggressiveness.MODERATE,
    ): CalorieTargets {
        val daily = when (goal) {
            GoalType.MAINTAIN -> expenditure
            GoalType.BULK -> expenditure * 1.05
            GoalType.CUT -> expenditure * when (aggressiveness) {
                Aggressiveness.CONSERVATIVE -> 0.90
                Aggressiveness.MODERATE -> 0.85
                Aggressiveness.AGGRESSIVE -> 0.70
            }
        }
        return CalorieTargets(daily, daily * 7.0)
    }

    /** Keeps a maintenance goal metabolically honest: a 0.15%/week nudge while off target. */
    fun dynamicMaintenance(expenditure: Double, currentKg: Double, targetKg: Double): Double =
        if (Math.abs(currentKg - targetKg) <= MAINTAIN_TOLERANCE_KG) expenditure
        else if (currentKg > targetKg) expenditure * 0.9985
        else expenditure * 1.0015
}

object MacroPlanner {

    private val CUT_PROTEIN = mapOf(
        ProteinTier.LOW to 2.0,
        ProteinTier.MODERATE to 2.5,
        ProteinTier.HIGH to 3.0,
        ProteinTier.EXTRA to 3.5,
    )
    private val OTHER_PROTEIN = mapOf(
        ProteinTier.LOW to 1.75,
        ProteinTier.MODERATE to 2.35,
        ProteinTier.HIGH to 2.75,
        ProteinTier.EXTRA to 3.1,
    )

    const val KETO_MAX_CARB_G = 30.0

    fun proteinPerKgFatFreeMass(goal: GoalType, tier: ProteinTier): Double =
        if (goal == GoalType.CUT) CUT_PROTEIN.getValue(tier) else OTHER_PROTEIN.getValue(tier)

    fun plan(
        kcal: Double,
        bodyWeightKg: Double,
        fatFreeMassKg: Double?,
        goal: GoalType,
        tier: ProteinTier,
        diet: DietStyle,
    ): MacroTargets {
        val perKgFfm = proteinPerKgFatFreeMass(goal, tier)
        val proteinG = if (fatFreeMassKg != null) perKgFfm * fatFreeMassKg
        else perKgFfm * 0.75 * bodyWeightKg

        val fatFloorG = 0.5 * bodyWeightKg
        var fatG = maxOf(kcal * diet.fatShare / 9.0, fatFloorG)
        var carbG = (kcal - proteinG * 4.0 - fatG * 9.0) / 4.0

        if (carbG < 0.0) {
            fatG = fatFloorG
            carbG = (kcal - proteinG * 4.0 - fatG * 9.0) / 4.0
        }
        if (carbG < 0.0) {
            carbG = 0.0
        }
        if (diet == DietStyle.KETO && carbG > KETO_MAX_CARB_G) {
            val excessKcal = (carbG - KETO_MAX_CARB_G) * 4.0
            carbG = KETO_MAX_CARB_G
            fatG += excessKcal / 9.0
        }

        return MacroTargets(
            kcal = Math.round(kcal).toInt(),
            proteinG = Math.round(proteinG).toInt(),
            fatG = Math.round(fatG).toInt(),
            carbG = Math.round(carbG).toInt(),
        )
    }
}
