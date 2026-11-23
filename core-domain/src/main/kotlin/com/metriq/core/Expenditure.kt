package com.metriq.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Confidence { HIGH, MEDIUM, LOW }

data class ExpenditureResult(
    val date: LocalDate,
    val kcal: Double,
    val confidence: Confidence,
    val updated: Boolean,
    val samples: Int,
)

/**
 * Energy expenditure from the energy-balance identity:
 * calories in - change in stored energy = calories out.
 *
 * Updates pause when more than 3 of the last 7 days have no nutrition logged.
 */
object Expenditure {

    /** Symmetric energy density (~3500 kcal/lb). */
    const val KCAL_PER_KG = 7716.0

    private const val WINDOW_DAYS = 14
    private const val MAX_MISSING_DAYS_IN_WEEK = 3
    private const val FALLBACK_BMR_KCAL = 1500.0
    private const val MIN_BMR_FACTOR = 0.9
    private const val MAX_BMR_FACTOR = 2.0
    private const val PLAUSIBLE_ACTIVE_KCAL_SHARE = 1.0

    fun estimate(
        date: LocalDate,
        intake: Map<LocalDate, Double?>,
        trend: Map<LocalDate, Double>,
        bmr: Double,
        weights: Map<LocalDate, Double>,
        previous: ExpenditureResult? = null,
        observedActiveKcal: Double? = null,
    ): ExpenditureResult {
        // A pathological profile can produce a negative or non-finite BMR. A NaN bound
        // would silently disable the plausibility clamp, so use a neutral adult value.
        val safeBmr = if (bmr.isFinite() && bmr > 0.0) bmr else FALLBACK_BMR_KCAL
        val missingInWeek = (0..6).count { offset ->
            intake[date.minusDays(offset.toLong())] == null
        }
        if (missingInWeek > MAX_MISSING_DAYS_IN_WEEK) {
            val fallback = previous ?: ExpenditureResult(date, safeBmr * 1.4, Confidence.LOW, false, 0)
            return fallback.copy(date = date, updated = false, confidence = Confidence.LOW)
        }

        // Stored energy must come from ACTUAL weigh-ins. The trend holds flat on days
        // without a weigh-in, so trend[d]-trend[d-1] is zero on those days and would
        // turn every gap day into an intake-only sample. A real weigh-in's kg change is
        // spread evenly over the days since the previous real weigh-in.
        val storedByDay = storedEnergyPerDay(weights, date)
        val samples = ArrayList<Pair<Double, Double>>(WINDOW_DAYS)
        for (offset in 0 until WINDOW_DAYS) {
            val day = date.minusDays(offset.toLong())
            val eaten = intake[day] ?: continue
            val stored = storedByDay[day] ?: continue
            // Recent days weigh more.
            val weight = (WINDOW_DAYS - offset).toDouble()
            samples.add((eaten - stored) to weight)
        }

        if (samples.isEmpty()) {
            val fallback = previous ?: ExpenditureResult(date, safeBmr * 1.4, Confidence.LOW, false, 0)
            return fallback.copy(date = date, updated = false, confidence = Confidence.LOW)
        }

        val smoothed = weightedMedian(samples).coerceIn(
            safeBmr * MIN_BMR_FACTOR,
            safeBmr * MAX_BMR_FACTOR,
        )

        // Trend and observed activity are confidence/plausibility inputs only. Adding
        // observedActiveKcal to the value would double-count activity already embedded
        // in the intake-vs-weight energy balance.
        val trendDays = (0 until WINDOW_DAYS).count { trend[date.minusDays(it.toLong())] != null }
        var confidence = when {
            samples.size >= 10 && missingInWeek == 0 -> Confidence.HIGH
            samples.size >= 5 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (trendDays < WINDOW_DAYS / 2) confidence = downgrade(confidence)
        if (observedActiveKcal != null && observedActiveKcal.isFinite() &&
            observedActiveKcal > smoothed * PLAUSIBLE_ACTIVE_KCAL_SHARE
        ) {
            confidence = Confidence.LOW
        }
        return ExpenditureResult(date, smoothed, confidence, true, samples.size)
    }

    /**
     * Per-day stored energy for the days in the 14-day window that are covered by a
     * pair of real weigh-ins. The kg change between consecutive weigh-ins is divided
     * by the true calendar gap (so a 7-day gap contributes 1/7 of the change per day),
     * but only days inside the window become samples.
     */
    private fun storedEnergyPerDay(
        weights: Map<LocalDate, Double>,
        date: LocalDate,
    ): Map<LocalDate, Double> {
        val windowStart = date.minusDays((WINDOW_DAYS - 1).toLong())
        val observed = weights.entries
            .filter { (day, kg) -> !day.isAfter(date) && kg.isFinite() }
            .sortedBy { it.key }
        if (observed.size < 2) return emptyMap()

        val result = HashMap<LocalDate, Double>()
        for (i in 1 until observed.size) {
            val (fromDay, fromKg) = observed[i - 1]
            val (toDay, toKg) = observed[i]
            val gap = ChronoUnit.DAYS.between(fromDay, toDay)
            if (gap <= 0) continue
            val storedPerDay = (toKg - fromKg) / gap.toDouble() * KCAL_PER_KG
            var day = if (fromDay.isBefore(windowStart)) windowStart else fromDay.plusDays(1)
            while (!day.isAfter(toDay) && !day.isAfter(date)) {
                result[day] = storedPerDay
                day = day.plusDays(1)
            }
        }
        return result
    }

    private fun downgrade(confidence: Confidence): Confidence = when (confidence) {
        Confidence.HIGH -> Confidence.MEDIUM
        Confidence.MEDIUM, Confidence.LOW -> Confidence.LOW
    }

    private fun weightedMedian(values: List<Pair<Double, Double>>): Double {
        val sorted = values.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        var acc = 0.0
        for ((value, weight) in sorted) {
            acc += weight
            if (acc >= total / 2.0) return value
        }
        return sorted.last().first
    }
}
