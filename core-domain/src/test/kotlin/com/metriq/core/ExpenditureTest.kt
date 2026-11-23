package com.metriq.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpenditureTest {

    private val day0: LocalDate = LocalDate.of(2026, 1, 1)

    private fun d(n: Int): LocalDate = day0.plusDays(n.toLong())

    private fun flatTrend(days: Int, kg: Double = 80.0): Map<LocalDate, Double> =
        (0..days).associate { d(it) to kg }

    private fun intake(days: Int, kcal: Double): Map<LocalDate, Double?> =
        (0..days).associate { d(it) to kcal }

    @Test
    fun `stable weight recovers logged intake as expenditure`() {
        val result = Expenditure.estimate(
            date = d(10),
            intake = intake(10, 2500.0),
            trend = flatTrend(10),
            bmr = 2000.0,
            weights = flatTrend(10),
        )
        assertTrue(result.updated)
        assertEquals(2500.0, result.kcal, 1.0)
    }

    @Test
    fun `gaining weight lowers the estimate below intake`() {
        val weights = (0..10).associate { d(it) to 80.0 + it * 0.1 }
        val trend = TrendWeight.compute(weights, d(0), d(10))
        // BMR 1500 keeps 1728.4 above the tightened 0.9 * BMR plausibility floor.
        val result = Expenditure.estimate(d(10), intake(10, 2500.0), trend, 1500.0, weights)
        assertTrue(result.kcal < 2500.0)
        assertEquals(2500.0 - 0.1 * Expenditure.KCAL_PER_KG, result.kcal, 1.0)
    }

    @Test
    fun `four missing days pause the update`() {
        val intake = intake(10, 2500.0).toMutableMap()
        (7..10).forEach { intake[d(it)] = null }
        val previous = ExpenditureResult(d(9), 2400.0, Confidence.MEDIUM, true, 10)
        val result = Expenditure.estimate(d(10), intake, flatTrend(10), 2000.0, emptyMap(), previous)
        assertTrue(!result.updated)
        assertEquals(2400.0, result.kcal, 0.001)
    }

    @Test
    fun `three missing days still updates`() {
        val intake = intake(10, 2500.0).toMutableMap()
        (8..10).forEach { intake[d(it)] = null }
        val result = Expenditure.estimate(d(10), intake, flatTrend(10), 2000.0, flatTrend(10))
        assertTrue(result.updated)
    }

    @Test
    fun `estimate stays inside the plausibility clamp`() {
        val weights = (0..10).associate { d(it) to 80.0 + it * 1.0 }
        val trend = TrendWeight.compute(weights, d(0), d(10))
        val result = Expenditure.estimate(d(10), intake(10, 1500.0), trend, 2000.0, weights)
        assertTrue(result.kcal >= 1800.0)
    }

    @Test
    fun `negative bmr still clamps the estimate`() {
        val weights = (0..10).associate { d(it) to 80.0 + it.toDouble() }
        val trend = TrendWeight.compute(weights, d(0), d(10))
        val result = Expenditure.estimate(d(10), intake(10, 1000.0), trend, -18.56, weights)
        assertTrue(result.kcal.isFinite())
        assertEquals(1350.0, result.kcal, 0.001)
    }

    @Test
    fun `nan bmr still clamps the estimate`() {
        val weights = (0..10).associate { d(it) to 80.0 + it.toDouble() }
        val trend = TrendWeight.compute(weights, d(0), d(10))
        val result = Expenditure.estimate(d(10), intake(10, 1000.0), trend, Double.NaN, weights)
        assertTrue(result.kcal.isFinite())
        assertEquals(1350.0, result.kcal, 0.001)
    }
}
