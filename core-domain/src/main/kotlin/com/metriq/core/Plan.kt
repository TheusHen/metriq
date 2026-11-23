package com.metriq.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

data class MacroTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbG: Int,
    /** True when the protein/fat floors had to be broken to fit the calorie target. */
    val floorsRelaxed: Boolean = false,
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

    private const val FAT_FLOOR_PER_KG = 0.5
    /** Absolute lower bound fat is only reduced past when the kcal target leaves no alternative. */
    private const val MIN_SURVIVAL_FAT_G = 10.0
    private const val SURVIVAL_FAT_G_PER_KG = 0.2

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
        val safeKcal = if (kcal.isFinite()) kcal.coerceAtLeast(0.0) else 0.0
        val perKgFfm = proteinPerKgFatFreeMass(goal, tier)
        // Mirror GoalEngine: impossible or non-finite composition falls back to a
        // fraction of body weight so protein can never go negative.
        val usableFfm = fatFreeMassKg?.takeIf { it.isFinite() && it > 0.0 }
        val proteinG = if (usableFfm != null) perKgFfm * usableFfm
        else perKgFfm * 0.75 * (if (bodyWeightKg.isFinite()) bodyWeightKg.coerceAtLeast(0.0) else 0.0)

        // Same ceiling as MacroEngine.buildAdaptation, so a planned day never sits
        // below the floor the adaptation step will apply.
        val fatFloorG = ceil(FAT_FLOOR_PER_KG * (if (bodyWeightKg.isFinite()) bodyWeightKg.coerceAtLeast(0.0) else 0.0))
        var fatG = maxOf(safeKcal * diet.fatShare / 9.0, fatFloorG)
        var carbG = (safeKcal - proteinG * 4.0 - fatG * 9.0) / 4.0

        if (carbG < 0.0) {
            fatG = fatFloorG
            carbG = (safeKcal - proteinG * 4.0 - fatG * 9.0) / 4.0
        }
        if (carbG < 0.0) {
            carbG = 0.0
        }
        if (diet == DietStyle.KETO && carbG > KETO_MAX_CARB_G) {
            val excessKcal = (carbG - KETO_MAX_CARB_G) * 4.0
            carbG = KETO_MAX_CARB_G
            fatG += excessKcal / 9.0
        }

        if (proteinG * 4.0 + fatG * 9.0 + carbG * 4.0 > safeKcal + 0.0001) {
            return fitToKcal(proteinG, fatG, safeKcal, bodyWeightKg)
        }

        return MacroTargets(
            kcal = Math.round(safeKcal).toInt(),
            proteinG = Math.round(proteinG).toInt(),
            fatG = Math.round(fatG).toInt(),
            carbG = Math.round(carbG).toInt(),
        )
    }

    /**
     * Floors cannot fit inside the calorie budget. Reduce fat toward the minimal
     * survival floor first, then protein, then (only if the target is below even that)
     * fat below the survival floor. Carbohydrates absorb the rounding remainder so the
     * integer macros add back to the target within 2 kcal.
     */
    private fun fitToKcal(
        proteinG: Double,
        fatG: Double,
        kcal: Double,
        bodyWeightKg: Double,
    ): MacroTargets {
        val survivalFatG = maxOf(
            MIN_SURVIVAL_FAT_G,
            SURVIVAL_FAT_G_PER_KG * (if (bodyWeightKg.isFinite()) bodyWeightKg.coerceAtLeast(0.0) else 0.0),
        )
        var protein = proteinG.coerceAtLeast(0.0)
        var fat = fatG.coerceAtLeast(0.0)
        var energy = 4.0 * protein + 9.0 * fat

        if (energy > kcal) {
            val reduction = min((energy - kcal) / 9.0, (fat - survivalFatG).coerceAtLeast(0.0))
            fat -= reduction
            energy -= reduction * 9.0
        }
        if (energy > kcal) {
            val reduction = min((energy - kcal) / 4.0, protein)
            protein -= reduction
            energy -= reduction * 4.0
        }
        if (energy > kcal) {
            fat = (kcal / 9.0).coerceAtLeast(0.0)
            energy = 9.0 * fat
        }

        // Floor protein and fat so rounding can only leave energy unused, which the
        // carbohydrate rounding then picks back up.
        val roundedProtein = floor(protein).toInt().coerceAtLeast(0)
        val roundedFat = floor(fat).toInt().coerceAtLeast(0)
        val roundedCarb = Math.round((kcal - 4.0 * roundedProtein - 9.0 * roundedFat) / 4.0)
            .toInt().coerceAtLeast(0)

        return MacroTargets(
            kcal = Math.round(kcal).toInt(),
            proteinG = roundedProtein,
            fatG = roundedFat,
            carbG = roundedCarb,
            floorsRelaxed = true,
        )
    }
}
