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

    fun estimate(
        date: LocalDate,
        intake: Map<LocalDate, Double?>,
        trend: Map<LocalDate, Double>,
        bmr: Double,
        previous: ExpenditureResult? = null,
    ): ExpenditureResult {
        val missingInWeek = (0..6).count { offset ->
            intake[date.minusDays(offset.toLong())] == null
        }
        if (missingInWeek > MAX_MISSING_DAYS_IN_WEEK) {
            val fallback = previous ?: ExpenditureResult(date, bmr * 1.4, Confidence.LOW, false, 0)
            return fallback.copy(date = date, updated = false, confidence = Confidence.LOW)
        }

        val samples = ArrayList<Pair<Double, Double>>(WINDOW_DAYS)
        for (offset in 0 until WINDOW_DAYS) {
            val day = date.minusDays(offset.toLong())
            val eaten = intake[day] ?: continue
            val today = trend[day] ?: continue
            val yesterday = trend[day.minusDays(1)] ?: continue
            val stored = (today - yesterday) * KCAL_PER_KG
            // Recent days weigh more.
            val weight = (WINDOW_DAYS - offset).toDouble()
            samples.add((eaten - stored) to weight)
        }

        if (samples.isEmpty()) {
            val fallback = previous ?: ExpenditureResult(date, bmr * 1.4, Confidence.LOW, false, 0)
            return fallback.copy(date = date, updated = false, confidence = Confidence.LOW)
        }

        val smoothed = weightedMedian(samples).coerceIn(bmr * 0.6, bmr * 2.5)
        val confidence = when {
            samples.size >= 10 && missingInWeek == 0 -> Confidence.HIGH
            samples.size >= 5 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return ExpenditureResult(date, smoothed, confidence, true, samples.size)
    }

    fun daysWithoutIntake(intake: Map<LocalDate, Double?>, date: LocalDate, days: Int = 7): Int =
        (0 until days).count { intake[date.minusDays(it.toLong())] == null }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

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
