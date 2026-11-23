package com.metriq.app.ui.onboarding

import com.metriq.core.PlanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression proof that onboarding persists the reviewed plan as a goal override. */
class OnboardingGoalTest {

    private fun plan(kcal: Double) = PlanResult(
        bmr = 1500.0,
        tdee = 2000.0,
        dailyKcal = kcal,
        proteinG = 120,
        fatG = 60,
        carbG = 200,
        isMinor = false,
        warnings = emptyList(),
        explanation = "",
    )

    @Test
    fun `uses reviewed plan kcal and chosen target and weeks`() {
        val goal = buildOnboardingGoal(plan(1850.4), 78.5, 12)

        assertEquals(1850, goal?.dailyKcal)
        assertEquals(78.5, goal!!.targetWeightKg!!, 0.0)
        assertEquals(12, goal?.weeks)
    }

    @Test
    fun `keeps null target and weeks when not chosen`() {
        val goal = buildOnboardingGoal(plan(2000.0), null, null)

        assertEquals(2000, goal?.dailyKcal)
        assertNull(goal?.targetWeightKg)
        assertNull(goal?.weeks)
    }

    @Test
    fun `does not persist anything without a positive plan`() {
        assertNull(buildOnboardingGoal(null, 80.0, 4))
        assertNull(buildOnboardingGoal(plan(0.0), 80.0, 4))
    }

    @Test
    fun `follows current expenditure when a full deadline was chosen`() {
        val goal = buildOnboardingGoal(plan(1850.0), 78.5, 12)

        assertTrue(goal!!.followExpenditure)
        assertEquals(78.5, goal.targetWeightKg!!, 0.0)
        assertEquals(12, goal.weeks)
    }

    @Test
    fun `pins the onboarding kcal when no deadline was chosen`() {
        assertFalse(buildOnboardingGoal(plan(2000.0), null, null)!!.followExpenditure)
        assertFalse(buildOnboardingGoal(plan(2000.0), 78.5, null)!!.followExpenditure)
        assertFalse(buildOnboardingGoal(plan(2000.0), null, 12)!!.followExpenditure)
    }
}
