package com.metriq.core

import java.time.LocalDate

/**
 * Weight trend: gaps interpolated, then an exponential average that leans on recent
 * weigh-ins and stands still on days without data.
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
        val dense = interpolate(weights, days)

        val trend = LinkedHashMap<LocalDate, Double>(days.size)
        var current: Double? = null
        var lastWeighIn: LocalDate? = null

        days.forEachIndexed { index, day ->
            val observed = dense[index]
            if (observed != null) {
                val gapDays = lastWeighInGap(day, lastWeighIn)
                lastWeighIn = day
                if (current == null) {
                    current = observed
                } else {
                    val alpha = (MIN_ALPHA + ALPHA_STEP * gapDays).coerceIn(MIN_ALPHA, MAX_ALPHA)
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

    /** Linear interpolation between known weigh-ins; flat hold at both ends. */
    private fun interpolate(weights: Map<LocalDate, Double>, days: List<LocalDate>): List<Double?> {
        val raw = days.map { weights[it] }
        val known = raw.indices.filter { raw[it] != null }
        if (known.isEmpty()) return raw
        if (known.size == 1) return raw.map { raw[known.first()] }

        val out = raw.toMutableList()
        for (i in 0 until known.first()) out[i] = raw[known.first()]
        for (k in 0 until known.size - 1) {
            val a = known[k]
            val b = known[k + 1]
            val va = raw[a]!!
            val vb = raw[b]!!
            for (i in a + 1 until b) {
                val t = (i - a).toDouble() / (b - a).toDouble()
                out[i] = va + (vb - va) * t
            }
        }
        for (i in known.last() + 1 until raw.size) out[i] = raw[known.last()]
        return out
    }
}
