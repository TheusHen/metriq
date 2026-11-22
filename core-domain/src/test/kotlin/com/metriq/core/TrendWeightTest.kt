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
    fun `missing days are interpolated before smoothing`() {
        val weights = mapOf(d(0) to 80.0, d(2) to 82.0)
        val trend = TrendWeight.compute(weights, d(0), d(2))
        assertEquals(80.0, trend[d(0)]!!, 0.0001)
        // d(1) interpolates to 81 kg, then the 0.14 alpha pulls the trend a fifth of the way.
        assertEquals(80.14, trend[d(1)]!!, 0.0001)
        assertTrue(trend[d(2)]!! > trend[d(1)]!!)
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
    fun `change rate is weekly and needs a full span`() {
        val weights = (0..25).associate { d(it) to 80.0 - it * 0.1 }
        val trend = TrendWeight.compute(weights, d(0), d(25))
        val rate = TrendWeight.changeRatePerWeek(trend, d(25))
        assertTrue(rate != null && rate < 0.0)
        assertNull(TrendWeight.changeRatePerWeek(trend, d(5)))
    }
}
