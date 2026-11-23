package com.metriq.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendWeightTest {

    private val day0: LocalDate = LocalDate.of(2026, 1, 1)

    private fun d(n: Int): LocalDate = day0.plusDays(n.toLong())

    @Test
    fun `constant weight keeps a constant trend`() {
        val weights = (0..10).associate { d(it) to 80.0 }
        val trend = TrendWeight.compute(weights, d(0), d(10))
        assertEquals(11, trend.size)
        trend.values.forEach { assertEquals(80.0, it, 0.0001) }
    }

    @Test
    fun `unobserved days hold flat and the next weigh in uses the real gap`() {
        val weights = mapOf(d(0) to 80.0, d(2) to 82.0)
        val trend = TrendWeight.compute(weights, d(0), d(2))
        assertEquals(80.0, trend[d(0)]!!, 0.0001)
        // d(1) has no weigh-in, so the trend stands still. d(2) is two days after the
        // last actual weigh-in: alpha = 0.12 + 0.02 * (2 - 1) = 0.14.
        assertEquals(80.0, trend[d(1)]!!, 0.0001)
        assertEquals(80.28, trend[d(2)]!!, 0.0001)
    }

    @Test
    fun `one day gap uses the minimum alpha and long gaps reach the cap`() {
        val daily = TrendWeight.compute(mapOf(d(0) to 80.0, d(1) to 100.0), d(0), d(1))
        // A one-day gap must yield exactly MIN_ALPHA = 0.12: 80 + 0.12 * 20 = 82.4.
        assertEquals(82.4, daily[d(1)]!!, 0.0001)

        val sparse = TrendWeight.compute(mapOf(d(0) to 80.0, d(13) to 100.0), d(0), d(13))
        // 0.12 + 0.02 * (13 - 1) = 0.36, capped at MAX_ALPHA = 0.35.
        assertEquals(87.0, sparse[d(13)]!!, 0.0001)
    }

    @Test
    fun `days without a weigh in hold the trend flat`() {
        val weights = mapOf(d(0) to 80.0, d(5) to 80.0)
        val trend = TrendWeight.compute(weights, d(0), d(5))
        assertEquals(80.0, trend[d(3)]!!, 0.0001)
    }

    @Test
    fun `trend reacts to a drop but less than the raw weight`() {
        val weights = (0..6).associate { d(it) to 80.0 } + mapOf(d(7) to 78.0)
        val trend = TrendWeight.compute(weights, d(0), d(7))
        val drop = 80.0 - trend[d(7)]!!
        assertTrue(drop > 0.0)
        assertTrue(drop < 2.0)
    }

    @Test
    fun `sparse weigh ins use the real gap and reach the maximum alpha`() {
        val sparse = TrendWeight.compute(mapOf(d(0) to 80.0, d(10) to 90.0), d(0), d(10))
        assertEquals(80.0, sparse[d(5)]!!, 0.0001)
        // alpha = clamp(0.12 + 0.02 * (10 - 1), 0.12, 0.35) = 0.30
        assertEquals(83.0, sparse[d(10)]!!, 0.0001)

        val verySparse = TrendWeight.compute(mapOf(d(0) to 80.0, d(30) to 100.0), d(0), d(30))
        // The gap-based alpha saturates at MAX_ALPHA = 0.35.
        assertEquals(87.0, verySparse[d(30)]!!, 0.0001)
    }

    @Test
    fun `change rate is null for a zero span`() {
        assertNull(TrendWeight.changeRatePerWeek(mapOf(d(0) to 80.0), d(0), 0))
    }

    @Test
    fun `change rate is weekly and needs a full span`() {
        val weights = (0..25).associate { d(it) to 80.0 - it * 0.1 }
        val trend = TrendWeight.compute(weights, d(0), d(25))
        val rate = TrendWeight.changeRatePerWeek(trend, d(25))
        assertTrue(rate != null && rate < 0.0)
        assertNull(TrendWeight.changeRatePerWeek(trend, d(5)))
    }
}
