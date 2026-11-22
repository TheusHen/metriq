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
        )
        assertTrue(result.updated)
        assertEquals(2500.0, result.kcal, 1.0)
    }

    @Test
    fun `gaining weight lowers the estimate below intake`() {
        val trend = (0..10).associate { d(it) to 80.0 + it * 0.1 }
        val result = Expenditure.estimate(d(10), intake(10, 2500.0), trend, 2000.0)
        assertTrue(result.kcal < 2500.0)
        assertEquals(2500.0 - 0.1 * Expenditure.KCAL_PER_KG, result.kcal, 1.0)
    }

    @Test
    fun `four missing days pause the update`() {
        val intake = intake(10, 2500.0).toMutableMap()
        (7..10).forEach { intake[d(it)] = null }
        val previous = ExpenditureResult(d(9), 2400.0, Confidence.MEDIUM, true, 10)
        val result = Expenditure.estimate(d(10), intake, flatTrend(10), 2000.0, previous)
        assertTrue(!result.updated)
        assertEquals(2400.0, result.kcal, 0.001)
    }

    @Test
    fun `three missing days still updates`() {
        val intake = intake(10, 2500.0).toMutableMap()
        (8..10).forEach { intake[d(it)] = null }
        val result = Expenditure.estimate(d(10), intake, flatTrend(10), 2000.0)
        assertTrue(result.updated)
    }

    @Test
    fun `estimate stays inside the plausibility clamp`() {
        val trend = (0..10).associate { d(it) to 80.0 + it * 1.0 }
        val result = Expenditure.estimate(d(10), intake(10, 1500.0), trend, 2000.0)
        assertTrue(result.kcal >= 1200.0)
    }
}
