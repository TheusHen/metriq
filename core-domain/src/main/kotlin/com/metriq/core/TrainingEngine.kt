package com.metriq.core

/**
 * A rich, source-agnostic daily training signal.
 *
 * `TrainingIntensity` is deliberately *not* redeclared here: it lives in
 * [MacroEngine] and is shared by the dashboard and the onboarding engines.
 */
data class TrainingSignal(
    val activeKcal: Double,
    val steps: Long?,
    val sessions: Int,
    val volumeKg: Double,
    val durationMinutes: Int,
    val muscles: Set<String>,
    val intensity: TrainingIntensity,
)

/**
 * Turns raw Health Connect / Hevy readings into a sanitized [TrainingSignal].
 *
 * Every numeric input is clamped before use: negatives and non-finite doubles
 * become 0, negative counts become 0 and blank muscle names are dropped. The
 * object is pure and deterministic (no clock, no I/O, no Android APIs).
 */
object TrainingEngine {

    private const val MIN_ACTIVE_KCAL = 5.0
    private const val MIN_DURATION_MINUTES = 5
    private const val LIGHT_MINUTES = 25
    private const val MODERATE_MINUTES = 60

    // Volume lifted per kg of bodyweight. ~30 kg/kg is a light session,
    // ~70 kg/kg a serious one; duration can equally promote a day a tier.
    private const val LIGHT_VOLUME_PER_KG = 30.0
    private const val MODERATE_VOLUME_PER_KG = 70.0

    // Supporting energy signal, expressed per kg of bodyweight.
    private const val LIGHT_KCAL_PER_KG = 3.0
    private const val MODERATE_KCAL_PER_KG = 6.0

    private const val WINDOW_DAYS = 7
    private const val REFERENCE_DAILY_ACTIVE_KCAL = 600.0
    private const val REFERENCE_DAILY_MINUTES = 60.0

    /** Health Connect only: energy, steps, sessions and duration. */
    fun fromHealthConnect(
        activeKcal: Double,
        steps: Long?,
        sessions: Int,
        durationMinutes: Int,
        bodyWeightKg: Double,
    ): TrainingSignal {
        val kcal = safeDouble(activeKcal)
        val safeSteps = steps?.coerceAtLeast(0L)
        val count = sessions.coerceAtLeast(0)
        val minutes = durationMinutes.coerceAtLeast(0)
        val weight = safeDouble(bodyWeightKg)
        val intensity = MacroEngine.intensityOf(kcal, minutes, count, weight)
        return TrainingSignal(
            activeKcal = kcal,
            steps = safeSteps,
            sessions = count,
            volumeKg = 0.0,
            durationMinutes = minutes,
            muscles = emptySet(),
            intensity = intensity,
        )
    }

    /**
     * Health Connect energy/steps enriched with Hevy volume, duration and
     * muscle coverage. Volume per kg and duration set the base tier; active
     * kcal can lift it by at most one tier as a supporting signal.
     */
    fun fromHevy(
        activeKcal: Double,
        steps: Long?,
        volumeKg: Double,
        durationMinutes: Int,
        muscles: Set<String>,
        sessions: Int,
        bodyWeightKg: Double,
    ): TrainingSignal {
        val kcal = safeDouble(activeKcal)
        val safeSteps = steps?.coerceAtLeast(0L)
        val volume = safeDouble(volumeKg)
        val minutes = durationMinutes.coerceAtLeast(0)
        val count = sessions.coerceAtLeast(0)
        val weight = safeDouble(bodyWeightKg)
        val safeMuscles = muscles.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val intensity = hevyIntensity(kcal, volume, minutes, count, weight)
        return TrainingSignal(
            activeKcal = kcal,
            steps = safeSteps,
            sessions = count,
            volumeKg = volume,
            durationMinutes = minutes,
            muscles = safeMuscles,
            intensity = intensity,
        )
    }

    /** Normalized rolling load over the last (up to) seven signals, in 0..1. */
    fun weeklyLoad(signals: List<TrainingSignal>): Double {
        if (signals.isEmpty()) return 0.0
        val window = signals.takeLast(WINDOW_DAYS)
        return (window.sumOf { dailyLoad(it) } / window.size).coerceIn(0.0, 1.0)
    }

    private fun hevyIntensity(
        kcal: Double,
        volumeKg: Double,
        minutes: Int,
        sessions: Int,
        bodyWeightKg: Double,
    ): TrainingIntensity {
        if (sessions <= 0 || (minutes < MIN_DURATION_MINUTES && kcal < MIN_ACTIVE_KCAL && volumeKg <= 0.0)) {
            return TrainingIntensity.REST
        }

        val volumePerKg = if (bodyWeightKg > 0.0) volumeKg / bodyWeightKg else 0.0
        val kcalPerKg = if (bodyWeightKg > 0.0) kcal / bodyWeightKg else 0.0

        val volumeTier = when {
            volumePerKg >= MODERATE_VOLUME_PER_KG -> 2
            volumePerKg >= LIGHT_VOLUME_PER_KG -> 1
            else -> 0
        }
        val durationTier = when {
            minutes >= MODERATE_MINUTES -> 2
            minutes >= LIGHT_MINUTES -> 1
            else -> 0
        }
        val kcalTier = when {
            kcalPerKg >= MODERATE_KCAL_PER_KG -> 2
            kcalPerKg >= LIGHT_KCAL_PER_KG -> 1
            else -> 0
        }

        // Volume and duration dominate; energy may promote at most one tier.
        val base = maxOf(volumeTier, durationTier)
        val tier = minOf(2, maxOf(base, minOf(kcalTier, base + 1)))
        return when (tier) {
            0 -> TrainingIntensity.LIGHT
            1 -> TrainingIntensity.MODERATE
            else -> TrainingIntensity.HARD
        }
    }

    private fun dailyLoad(signal: TrainingSignal): Double {
        val intensityPart = when (signal.intensity) {
            TrainingIntensity.REST -> 0.0
            TrainingIntensity.LIGHT -> 1.0 / 3.0
            TrainingIntensity.MODERATE -> 2.0 / 3.0
            TrainingIntensity.HARD -> 1.0
        }
        if (intensityPart == 0.0) return 0.0

        val kcalPart = (safeDouble(signal.activeKcal) / REFERENCE_DAILY_ACTIVE_KCAL).coerceIn(0.0, 1.0)
        val minutePart = (signal.durationMinutes.coerceAtLeast(0).toDouble() / REFERENCE_DAILY_MINUTES)
            .coerceIn(0.0, 1.0)
        return ((intensityPart + kcalPart + minutePart) / 3.0).coerceIn(0.0, 1.0)
    }

    private fun safeDouble(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
}
