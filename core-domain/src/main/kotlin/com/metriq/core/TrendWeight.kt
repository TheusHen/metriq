package com.metriq.core

import java.time.LocalDate

/**
 * Weight trend: an exponential average applied only on actual weigh-in days that
 * leans harder on the reading the longer it has been since the previous weigh-in.
 * Days without data hold the previous trend.
 */
object TrendWeight {

    private const val MIN_ALPHA = 0.12
    private const val MAX_ALPHA = 0.35
    private const val ALPHA_STEP = 0.02

    fun compute(
        weights: Map<LocalDate, Double>,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Double> {
        val days = dayRange(from, to)
        if (days.isEmpty()) return emptyMap()

        val trend = LinkedHashMap<LocalDate, Double>(days.size)
        var current: Double? = null
        var lastWeighIn: LocalDate? = null

        days.forEach { day ->
            // Only a real weigh-in moves the trend. The alpha is driven by the gap
            // since the last actual weigh-in, not by the presence of a trend value.
            val observed = weights[day]
            if (observed != null) {
                val gapDays = lastWeighInGap(day, lastWeighIn)
                lastWeighIn = day
                if (current == null) {
                    current = observed
                } else {
                    // gapDays counts calendar days between weigh-ins, so a daily
                    // weigh-in (gap 1) must use exactly MIN_ALPHA.
                    val alpha = (MIN_ALPHA + ALPHA_STEP * (gapDays - 1)).coerceIn(MIN_ALPHA, MAX_ALPHA)
                    current = current!! + alpha * (observed - current!!)
                }
            }
            current?.let { trend[day] = it }
        }
        return trend
    }

    /** Change over [spanDays] expressed per week. Null when history is too short. */
    fun changeRatePerWeek(
        trend: Map<LocalDate, Double>,
        date: LocalDate,
        spanDays: Int = 20,
    ): Double? {
        if (spanDays <= 0) return null
        val end = trend[date] ?: return null
        val start = trend[date.minusDays(spanDays.toLong())] ?: return null
        return (end - start) / spanDays * 7.0
    }

    private fun lastWeighInGap(day: LocalDate, lastWeighIn: LocalDate?): Int =
        if (lastWeighIn == null) 0 else java.time.temporal.ChronoUnit.DAYS.between(lastWeighIn, day).toInt()

    private fun dayRange(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val days = ArrayList<LocalDate>()
        var day = from
        while (!day.isAfter(to)) {
            days.add(day)
            day = day.plusDays(1)
        }
        return days
    }
}
