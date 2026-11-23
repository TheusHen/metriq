package com.metriq.core

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanTest {

    @Test
    fun `maintenance follows expenditure`() {
        val targets = CaloriePlan.target(2600.0, GoalType.MAINTAIN)
        assertEquals(2600.0, targets.dailyKcal, 0.001)
        assertEquals(18200.0, targets.weeklyKcal, 0.001)
    }

    @Test
    fun `cut multipliers follow aggressiveness`() {
        assertEquals(2340.0, CaloriePlan.target(2600.0, GoalType.CUT, Aggressiveness.CONSERVATIVE).dailyKcal, 0.001)
        assertEquals(2210.0, CaloriePlan.target(2600.0, GoalType.CUT, Aggressiveness.MODERATE).dailyKcal, 0.001)
        assertEquals(1820.0, CaloriePlan.target(2600.0, GoalType.CUT, Aggressiveness.AGGRESSIVE).dailyKcal, 0.001)
    }

    @Test
    fun `bulk adds five percent`() {
        assertEquals(2730.0, CaloriePlan.target(2600.0, GoalType.BULK).dailyKcal, 0.001)
    }

    @Test
    fun `dynamic maintenance holds inside tolerance and nudges outside`() {
        assertEquals(2600.0, CaloriePlan.dynamicMaintenance(2600.0, 80.0, 80.5), 0.001)
        assertTrue(CaloriePlan.dynamicMaintenance(2600.0, 82.0, 80.0) < 2600.0)
        assertTrue(CaloriePlan.dynamicMaintenance(2600.0, 78.0, 80.0) > 2600.0)
    }

    @Test
    fun `cut protein tiers track fat free mass`() {
        assertEquals(2.0, MacroPlanner.proteinPerKgFatFreeMass(GoalType.CUT, ProteinTier.LOW), 0.001)
        assertEquals(3.5, MacroPlanner.proteinPerKgFatFreeMass(GoalType.CUT, ProteinTier.EXTRA), 0.001)
        assertEquals(1.75, MacroPlanner.proteinPerKgFatFreeMass(GoalType.BULK, ProteinTier.LOW), 0.001)
    }

    @Test
    fun `macros sum back to the calorie target`() {
        val targets = MacroPlanner.plan(
            kcal = 2000.0,
            bodyWeightKg = 80.0,
            fatFreeMassKg = 65.0,
            goal = GoalType.CUT,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.BALANCED,
        )
        val total = targets.proteinG * 4 + targets.fatG * 9 + targets.carbG * 4
        assertEquals(targets.kcal.toDouble(), total.toDouble(), 12.0)
        assertEquals(163, targets.proteinG)
    }

    @Test
    fun `keto caps carbohydrate and gives the rest to fat`() {
        val targets = MacroPlanner.plan(2200.0, 80.0, 65.0, GoalType.MAINTAIN, ProteinTier.MODERATE, DietStyle.KETO)
        assertTrue(targets.carbG <= 30)
    }

    @Test
    fun `fat floor yields to the calorie target when the target is very small`() {
        val targets = MacroPlanner.plan(1200.0, 90.0, 60.0, GoalType.CUT, ProteinTier.EXTRA, DietStyle.HIGH_CARB_LOW_FAT)
        val total = targets.proteinG * 4.0 + targets.fatG * 9.0 + targets.carbG * 4.0
        // 210 g protein + 45 g fat already cost 1245 kcal at a 1200 kcal target.
        assertEquals(1200.0, total, 2.0)
        assertTrue(targets.floorsRelaxed)
        assertTrue(targets.proteinG >= 0 && targets.fatG >= 0 && targets.carbG >= 0)
    }

    @Test
    fun `protein and fat floors are relaxed to fit a 100 kcal target`() {
        val targets = MacroPlanner.plan(
            kcal = 100.0,
            bodyWeightKg = 78.0,
            fatFreeMassKg = null,
            goal = GoalType.MAINTAIN,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.BALANCED,
        )
        val total = targets.proteinG * 4.0 + targets.fatG * 9.0 + targets.carbG * 4.0
        assertEquals(100.0, total, 2.0)
        assertTrue(targets.floorsRelaxed)
        assertTrue(targets.proteinG >= 0 && targets.fatG >= 0 && targets.carbG >= 0)
    }

    @Test
    fun `fat floor uses the same ceiling as macro adaptation`() {
        val kg = 80.2
        val targets = MacroPlanner.plan(
            kcal = 1600.0,
            bodyWeightKg = kg,
            fatFreeMassKg = 60.0,
            goal = GoalType.CUT,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.HIGH_CARB_LOW_FAT,
        )
        // 0.5 * 80.2 = 40.1: rounding down leaves less fat than the adaptation floor.
        assertEquals(ceil(0.5 * kg).toInt(), targets.fatG)
        assertTrue(targets.carbG > 0)
    }

    @Test
    fun `invalid fat free mass falls back to a fraction of body weight`() {
        val negative = MacroPlanner.plan(
            kcal = 2000.0,
            bodyWeightKg = 78.0,
            fatFreeMassKg = -39.0,
            goal = GoalType.MAINTAIN,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.BALANCED,
        )
        val nonFinite = MacroPlanner.plan(
            kcal = 2000.0,
            bodyWeightKg = 78.0,
            fatFreeMassKg = Double.NaN,
            goal = GoalType.MAINTAIN,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.BALANCED,
        )

        // OTHER_PROTEIN[MODERATE] = 2.35 g/kg; fallback lean mass is 75% of 78 kg.
        assertEquals(137, negative.proteinG)
        assertEquals(137, nonFinite.proteinG)
        assertEquals(213, negative.carbG)
        assertTrue(negative.proteinG > 0)
        assertTrue(negative.carbG > 0)
    }

    @Test
    fun `missing body fat uses an estimated lean mass for protein`() {
        // OTHER_PROTEIN[MODERATE] = 2.35 g/kg; lean mass is estimated at 75% of body weight.
        val targets = MacroPlanner.plan(
            kcal = 2400.0,
            bodyWeightKg = 80.0,
            fatFreeMassKg = null,
            goal = GoalType.MAINTAIN,
            tier = ProteinTier.MODERATE,
            diet = DietStyle.BALANCED,
        )
        assertEquals(141, targets.proteinG)
        assertTrue(targets.fatG > 0)
        assertTrue(targets.carbG > 0)
        assertEquals(targets.kcal.toDouble(), (targets.proteinG * 4 + targets.fatG * 9 + targets.carbG * 4).toDouble(), 12.0)
    }
}
