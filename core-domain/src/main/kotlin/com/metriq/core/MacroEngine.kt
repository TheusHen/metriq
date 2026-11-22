package com.metriq.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

enum class TrainingIntensity { REST, LIGHT, MODERATE, HARD }

data class TrainingLoad(
    val activeKcal: Double,
    val exerciseMinutes: Int,
    val sessions: Int,
    val intensity: TrainingIntensity,
)

data class MacroAdaptation(
    val targets: MacroTargets,
    val intensity: TrainingIntensity,
    val carbDeltaG: Int,
    val fatDeltaG: Int,
    val reason: String,
)

/**
 * Pure, deterministic daily macro adjustment driven by the training signal.
 *
 * Extra carbohydrates on training days are paid for by fat at isoenergetic
 * exchange rates (4 kcal/g carb, 9 kcal/g fat). Protein is never touched, the
 * fat floor and carb floor are respected, and total energy is kept on target.
 */
object MacroEngine {

    private const val KCAL_PER_CARB_G = 4.0
    private const val KCAL_PER_FAT_G = 9.0
    private const val FAT_FLOOR_PER_KG = 0.5

    private const val MIN_ACTIVE_KCAL = 5.0
    private const val MIN_EXERCISE_MINUTES = 5
    private const val LIGHT_KCAL_PER_KG = 3.0
    private const val LIGHT_MINUTES = 25
    private const val MODERATE_KCAL_PER_KG = 6.0
    private const val MODERATE_MINUTES = 60

    private const val LIGHT_CARB_SHARE = 0.02
    private const val MODERATE_CARB_SHARE = 0.05
    private const val HARD_CARB_SHARE = 0.12
    private const val LIGHT_CARB_CAP_G = 10
    private const val MODERATE_CARB_CAP_G = 25
    private const val HARD_CARB_CAP_G = 60

    private const val ROLLING_DAYS = 7
    private const val REFERENCE_DAILY_ACTIVE_KCAL = 600.0
    private const val REFERENCE_DAILY_MINUTES = 60.0
    private const val OVERREACH_WEEKLY_LOAD = 0.8

    /** Classifies a day from activity energy, duration, frequency and body weight. */
    fun intensityOf(
        activeKcal: Double,
        exerciseMinutes: Int,
        sessions: Int,
        bodyWeightKg: Double,
    ): TrainingIntensity {
        val kcal = if (activeKcal.isFinite()) activeKcal.coerceAtLeast(0.0) else 0.0
        val minutes = exerciseMinutes.coerceAtLeast(0)
        val count = sessions.coerceAtLeast(0)
        val weight = if (bodyWeightKg.isFinite()) bodyWeightKg.coerceAtLeast(0.0) else 0.0

        if (count == 0 || kcal < MIN_ACTIVE_KCAL || minutes < MIN_EXERCISE_MINUTES) {
            return TrainingIntensity.REST
        }

        // A missing/invalid weight leaves only the duration signal to classify the day.
        val kcalPerKg = if (weight > 0.0) kcal / weight else 0.0
        return when {
            kcalPerKg < LIGHT_KCAL_PER_KG && minutes < LIGHT_MINUTES -> TrainingIntensity.LIGHT
            kcalPerKg < MODERATE_KCAL_PER_KG && minutes < MODERATE_MINUTES -> TrainingIntensity.MODERATE
            else -> TrainingIntensity.HARD
        }
    }

    /**
     * Normalized rolling load over the last (up to) seven days, in 0..1.
     * Each day combines intensity, active kcal (600 kcal = 1.0) and duration
     * (60 min = 1.0), each capped at 1.0; the window is then averaged.
     */
    fun weeklyLoad(loads: List<TrainingLoad>): Double {
        if (loads.isEmpty()) return 0.0
        val window = loads.takeLast(ROLLING_DAYS)
        val total = window.sumOf { dailyLoad(it) }
        return (total / window.size).coerceIn(0.0, 1.0)
    }

    private fun dailyLoad(load: TrainingLoad): Double {
        val intensityPart = when (load.intensity) {
            TrainingIntensity.REST -> 0.0
            TrainingIntensity.LIGHT -> 1.0 / 3.0
            TrainingIntensity.MODERATE -> 2.0 / 3.0
            TrainingIntensity.HARD -> 1.0
        }
        if (intensityPart == 0.0) return 0.0
        val safeKcal = if (load.activeKcal.isFinite()) load.activeKcal.coerceAtLeast(0.0) else 0.0
        val kcalPart = (safeKcal / REFERENCE_DAILY_ACTIVE_KCAL).coerceIn(0.0, 1.0)
        val minutePart = (load.exerciseMinutes.coerceAtLeast(0).toDouble() / REFERENCE_DAILY_MINUTES).coerceIn(0.0, 1.0)
        return ((intensityPart + kcalPart + minutePart) / 3.0).coerceIn(0.0, 1.0)
    }

    fun adapt(
        base: MacroTargets,
        profile: Profile,
        load: TrainingLoad?,
        recentLoads: List<TrainingLoad>,
        enabled: Boolean,
    ): MacroAdaptation {
        val intensity = load?.intensity ?: TrainingIntensity.REST

        if (!enabled) {
            return unchanged(base, intensity, "Adaptação desativada; macros mantidos.")
        }
        if (load == null || load.intensity == TrainingIntensity.REST) {
            return unchanged(base, intensity, "Sem sinal de treino hoje; macros mantidos.")
        }
        if (profile.diet == DietStyle.KETO) {
            return unchanged(base, intensity, "Dieta cetogênica: carboidrato mantido, sem ajuste por treino.")
        }

        var desiredCarbBumpG = when (intensity) {
            TrainingIntensity.LIGHT -> min((base.carbG * LIGHT_CARB_SHARE).roundToInt(), LIGHT_CARB_CAP_G)
            TrainingIntensity.MODERATE -> min((base.carbG * MODERATE_CARB_SHARE).roundToInt(), MODERATE_CARB_CAP_G)
            TrainingIntensity.HARD -> min((base.carbG * HARD_CARB_SHARE).roundToInt(), HARD_CARB_CAP_G)
            TrainingIntensity.REST -> 0 // handled above
        }.coerceAtLeast(0)

        // Avoid pushing harder when the rolling week is already overloaded.
        val overreaching = intensity == TrainingIntensity.HARD && weeklyLoad(recentLoads) > OVERREACH_WEEKLY_LOAD
        if (overreaching) desiredCarbBumpG /= 2
        return buildAdaptation(base, profile, intensity, desiredCarbBumpG, overreaching)
    }

    /**
     * Rich-signal variant used when Health Connect data is combined with Hevy
     * volume, duration and muscle coverage. The declared [TrainingSignal.intensity]
     * drives the allocation; the extra detail adds a modest, capped bonus for
     * genuinely demanding sessions. All of the [adapt] guardrails still apply.
     */
    fun adaptWithSignal(
        base: MacroTargets,
        profile: Profile,
        signal: TrainingSignal?,
        recentSignals: List<TrainingSignal>,
        enabled: Boolean,
    ): MacroAdaptation {
        val intensity = signal?.intensity ?: TrainingIntensity.REST

        if (!enabled) {
            return unchanged(base, intensity, "Adaptação desativada; sinal de treino ignorado, macros mantidos.")
        }
        if (signal == null || intensity == TrainingIntensity.REST) {
            return unchanged(base, intensity, "Sem sinal de treino hoje; macros mantidos.")
        }
        if (profile.diet == DietStyle.KETO) {
            return unchanged(base, intensity, "Dieta cetogênica: carboidrato mantido, sem ajuste por treino.")
        }

        val volume = if (signal.volumeKg.isFinite()) signal.volumeKg.coerceAtLeast(0.0) else 0.0
        val weight = if (profile.weightKg.isFinite()) profile.weightKg.coerceAtLeast(0.0) else 0.0
        val volumePerKg = if (weight > 0.0) volume / weight else 0.0
        val muscles = signal.muscles.size.coerceAtLeast(0)
        val minutes = signal.durationMinutes.coerceAtLeast(0)

        val shareBump = when (intensity) {
            TrainingIntensity.LIGHT -> min((base.carbG * LIGHT_CARB_SHARE).roundToInt(), LIGHT_CARB_CAP_G)
            TrainingIntensity.MODERATE -> min((base.carbG * MODERATE_CARB_SHARE).roundToInt(), MODERATE_CARB_CAP_G)
            TrainingIntensity.HARD -> min((base.carbG * HARD_CARB_SHARE).roundToInt(), HARD_CARB_CAP_G)
            TrainingIntensity.REST -> 0 // handled above
        }
        val muscleBonus = min(muscles / 2, 6)
        val volumeBonus = when {
            volumePerKg >= 70.0 -> 3
            volumePerKg >= 30.0 -> 1
            else -> 0
        }
        val durationBonus = if (minutes >= 60) 1 else 0
        val cap = when (intensity) {
            TrainingIntensity.LIGHT -> LIGHT_CARB_CAP_G
            TrainingIntensity.MODERATE -> MODERATE_CARB_CAP_G
            TrainingIntensity.HARD -> HARD_CARB_CAP_G
            TrainingIntensity.REST -> 0
        }
        var desiredCarbBumpG = (shareBump + muscleBonus + volumeBonus + durationBonus)
            .coerceAtMost(cap)
            .coerceAtLeast(0)

        val overreaching = intensity == TrainingIntensity.HARD &&
            TrainingEngine.weeklyLoad(recentSignals) > OVERREACH_WEEKLY_LOAD
        if (overreaching) desiredCarbBumpG /= 2

        val signalDetail = buildString {
            append(" Sinal de treino: ").append(minutes).append(" min")
            if (muscles > 0) append(", ").append(muscles).append(" grupamentos")
            if (volume > 0.0) append(", ").append(volume.toLong()).append(" kg de volume")
            append(".")
        }
        return buildAdaptation(base, profile, intensity, desiredCarbBumpG, overreaching, signalDetail)
    }

    private fun buildAdaptation(
        base: MacroTargets,
        profile: Profile,
        intensity: TrainingIntensity,
        desiredCarbBumpG: Int,
        overreaching: Boolean,
        reasonSuffix: String = "",
    ): MacroAdaptation {
        val weight = if (profile.weightKg.isFinite()) profile.weightKg.coerceAtLeast(0.0) else 0.0
        val fatFloorG = ceil(FAT_FLOOR_PER_KG * weight).toInt()
        val availableFatG = (base.fatG - fatFloorG).coerceAtLeast(0)
        val maxCarbGFromFat = (availableFatG * KCAL_PER_FAT_G / KCAL_PER_CARB_G).toInt()
        val carbBumpG = min(desiredCarbBumpG, maxCarbGFromFat).coerceAtLeast(0)

        if (carbBumpG == 0) {
            return unchanged(base, intensity, "Sem margem de gordura para realocar; carboidrato mantido.")
        }

        val fatToRemoveG = round(carbBumpG * KCAL_PER_CARB_G / KCAL_PER_FAT_G)
            .toInt()
            .coerceAtMost(availableFatG)
        val newFatG = base.fatG - fatToRemoveG

        // Recompute carbs so the macro energy stays on the planned calorie target.
        val energyForCarbs = base.kcal - base.proteinG * KCAL_PER_CARB_G - newFatG * KCAL_PER_FAT_G
        val newCarbG = max(0, round(energyForCarbs / KCAL_PER_CARB_G).toInt())

        val targets = MacroTargets(
            kcal = base.kcal,
            proteinG = base.proteinG,
            fatG = newFatG,
            carbG = newCarbG,
        )
        val carbDeltaG = newCarbG - base.carbG
        val fatDeltaG = newFatG - base.fatG

        val label = when (intensity) {
            TrainingIntensity.LIGHT -> "Treino leve"
            TrainingIntensity.MODERATE -> "Treino moderado"
            TrainingIntensity.HARD -> "Treino pesado"
            TrainingIntensity.REST -> "Sem treino"
        }
        val reason = buildString {
            append(label)
            append(": +").append(carbDeltaG).append(" g de carboidrato, ")
            append(fatDeltaG).append(" g de gordura.")
            if (overreaching) append(" Carga semanal alta: aumento reduzido.")
            append(reasonSuffix)
        }

        return MacroAdaptation(targets, intensity, carbDeltaG, fatDeltaG, reason)
    }

    private fun unchanged(
        base: MacroTargets,
        intensity: TrainingIntensity,
        reason: String,
    ): MacroAdaptation = MacroAdaptation(base, intensity, 0, 0, reason)
}
