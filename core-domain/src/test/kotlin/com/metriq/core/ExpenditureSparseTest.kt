package com.metriq.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression proof for the sparse-weigh-in defect: the old implementation sampled the
 * flattened trend, so days without a weigh-in contributed `stored = 0` and the weighted
 * median collapsed onto intake-only samples.
 *
 * Physical setup used by every case: intake 2200 kcal/day, real weight gain 0.1 kg/day,
 * therefore true expenditure = 2200 - 0.1 * 7716 = 1428.4 kcal/day. Weigh-ins happen only
 * every [gap] days; the trend is flat in between.
 */
class ExpenditureSparseTest {

    private val day0: LocalDate = LocalDate.of(2026, 1, 1)
    private val date = day0.plusDays(28)

    private fun d(n: Int): LocalDate = day0.plusDays(n.toLong())

    private fun weighIns(gap: Int): Map<LocalDate, Double> =
        (0..30 step gap).associate { d(it) to 80.0 + it * 0.1 }

    private fun scenario(gap: Int, observedActiveKcal: Double? = null): ExpenditureResult {
        val weights = weighIns(gap)
        val intake = (0..28).associate { d(it) to 2200.0 as Double? }
        val trend = TrendWeight.compute(weights, d(0), d(28))
        return Expenditure.estimate(
            date = date,
            intake = intake,
            trend = trend,
            bmr = 1500.0,
            weights = weights,
            observedActiveKcal = observedActiveKcal,
        )
    }

    @Test
    fun `daily weigh ins recover the physically correct expenditure`() {
        val result = scenario(gap = 1)
        assertEquals(1428.4, result.kcal, 1428.4 * 0.05)
    }

    @Test
    fun `three day weigh in gap keeps the physically correct expenditure`() {
        val result = scenario(gap = 3)
        assertEquals(1428.4, result.kcal, 1428.4 * 0.05)
    }

    @Test
    fun `seven day weigh in gap keeps the physically correct expenditure`() {
        val result = scenario(gap = 7)
        assertEquals(1428.4, result.kcal, 1428.4 * 0.05)
    }

    @Test
    fun `fourteen day weigh in gap keeps the physically correct expenditure`() {
        val result = scenario(gap = 14)
        assertEquals(1428.4, result.kcal, 1428.4 * 0.05)
    }

    @Test
    fun `observed active kcal never changes the returned estimate`() {
        val without = scenario(gap = 7)
        val withActivity = scenario(gap = 7, observedActiveKcal = 500.0)
        assertEquals(without.kcal, withActivity.kcal, 0.0001)
    }
}
