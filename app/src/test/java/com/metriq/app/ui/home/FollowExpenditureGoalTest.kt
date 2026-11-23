package com.metriq.app.ui.home

import com.metriq.core.Expenditure
import com.metriq.core.GoalOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.round

/** Pure rate-math tests for the "follow current expenditure" goal recompute. */
class FollowExpenditureGoalTest {

    @Test
    fun `recomputeDailyKcal uses onboarding rate math for a cut`() {
        // 80 -> 70 kg in 10 weeks => 1 kg/week deficit.
        val result = FollowExpenditureGoal.recomputeDailyKcal(
            currentExpenditureKcal = 2600.0,
            currentWeightKg = 80.0,
            targetWeightKg = 70.0,
            weeks = 10,
        )

        val expected = round(2600.0 - (10.0 / 10) * Expenditure.KCAL_PER_KG / 7.0).toInt()
        assertEquals(expected, result)
    }

    @Test
    fun `recomputeDailyKcal yields a surplus for a bulk`() {
        val result = FollowExpenditureGoal.recomputeDailyKcal(
            currentExpenditureKcal = 2400.0,
            currentWeightKg = 70.0,
            targetWeightKg = 75.0,
            weeks = 10,
        )

        val expected = round(2400.0 + (5.0 / 10) * Expenditure.KCAL_PER_KG / 7.0).toInt()
        assertEquals(expected, result)
    }

    @Test
    fun `recomputeDailyKcal returns null for invalid inputs`() {
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, 80.0, null, 10))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, 80.0, 0.0, 10))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, 80.0, Double.NaN, 10))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, 80.0, 70.0, null))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, 80.0, 70.0, 0))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(Double.NaN, 80.0, 70.0, 10))
        assertNull(FollowExpenditureGoal.recomputeDailyKcal(2600.0, Double.NaN, 70.0, 10))
    }

    @Test
    fun `recomputeDailyKcal clamps a physically impossible target to zero`() {
        val result = FollowExpenditureGoal.recomputeDailyKcal(
            currentExpenditureKcal = 1000.0,
            currentWeightKg = 80.0,
            targetWeightKg = 10.0,
            weeks = 1,
        )

        assertEquals(0, result)
    }

    @Test
    fun `resolve leaves a manual goal untouched`() {
        val manual = GoalOverride(2500, 70.0, 10, "manual") // followExpenditure defaults to false

        val resolved = FollowExpenditureGoal.resolve(manual, 2800.0, 80.0)

        assertSame(manual, resolved)
    }

    @Test
    fun `resolve recomputes an in-memory copy for a follow goal`() {
        val follow = GoalOverride(2500, 70.0, 10, "follow", followExpenditure = true)

        val resolved = FollowExpenditureGoal.resolve(follow, 2800.0, 80.0)

        val expected = round(2800.0 - (10.0 / 10) * Expenditure.KCAL_PER_KG / 7.0).toInt()
        assertEquals(expected, resolved.dailyKcal)
        assertEquals(follow.copy(dailyKcal = expected), resolved)
        // the stored override is never mutated
        assertEquals(2500, follow.dailyKcal)
    }

    @Test
    fun `resolve keeps the stored kcal when the target is invalid`() {
        val follow = GoalOverride(2500, null, null, "follow", followExpenditure = true)

        val resolved = FollowExpenditureGoal.resolve(follow, 2800.0, 80.0)

        assertSame(follow, resolved)
    }
}
