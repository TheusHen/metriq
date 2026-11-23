package com.metriq.app.ui.onboarding

import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.OnboardingInput
import com.metriq.core.OnboardingPlanner
import com.metriq.core.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Deadline feedback must come from OnboardingPlanner, never from a second copy of the maths. */
class DeadlinePlannerTest {

    private val today = LocalDate.of(2026, 11, 23)

    private fun input(
        age: Int = 30,
        weightKg: Double = 90.0,
        goal: GoalType = GoalType.CUT,
        targetWeightKg: Double? = 85.0,
        weeks: Int? = 10,
    ) = OnboardingInput(
        sex = Sex.MALE,
        age = age,
        heightCm = 180.0,
        weightKg = weightKg,
        activityLevel = ActivityLevel.MODERATE,
        goal = goal,
        diet = DietStyle.BALANCED,
        targetWeightKg = targetWeightKg,
        weeks = weeks,
    )

    @Test
    fun `uses the planner kcal and derives rate and target date for a realistic adult deadline`() {
        val i = input()
        val plan = OnboardingPlanner.plan(i)

        val feedback = DeadlinePlanner.feedback(plan, i, today)

        assertEquals(Math.round(plan.dailyKcal).toInt(), feedback!!.dailyKcal)
        assertEquals(0.5, feedback.kgPerWeek, 1e-9)
        assertEquals(0.5, feedback.referenceKgPerWeek, 1e-9)
        assertEquals(today.plusWeeks(10), feedback.targetDate)
        assertEquals(DeadlineAssessment.ON_TRACK, feedback.assessment)
        assertTrue(feedback.hasDeadline)
        assertTrue(feedback.isRealistic)
        assertFalse(feedback.isAboveSafe)
    }

    @Test
    fun `a minor deadline above 0_25 kg per week is flagged as aggressive`() {
        // 5 kg in 10 weeks = 0.5 kg/week, above the 0.25 minor reference.
        val i = input(age = 16, weightKg = 60.0, targetWeightKg = 55.0, weeks = 10)
        val plan = OnboardingPlanner.plan(i)

        val feedback = DeadlinePlanner.feedback(plan, i, today)

        assertEquals(0.25, feedback!!.referenceKgPerWeek, 1e-9)
        assertEquals(DeadlineAssessment.AGGRESSIVE, feedback.assessment)
        assertTrue(feedback.isAboveSafe)
        assertFalse(feedback.isRealistic)
        assertEquals(Math.round(plan.dailyKcal).toInt(), feedback.dailyKcal)
    }

    @Test
    fun `a deadline exactly at the adult reference stays on track`() {
        val i = input(weightKg = 90.0, targetWeightKg = 85.0, weeks = 10)
        val feedback = DeadlinePlanner.feedback(OnboardingPlanner.plan(i), i, today)

        assertEquals(DeadlineAssessment.ON_TRACK, feedback!!.assessment)
    }

    @Test
    fun `a target incompatible with the chosen goal is flagged even when the rate looks safe`() {
        val i = input(goal = GoalType.CUT, weightKg = 80.0, targetWeightKg = 85.0, weeks = 50)
        val feedback = DeadlinePlanner.feedback(OnboardingPlanner.plan(i), i, today)

        assertEquals(DeadlineAssessment.INCOMPATIBLE, feedback!!.assessment)
        assertFalse(feedback.isRealistic)
    }

    @Test
    fun `no target or no deadline keeps the plain plan behaviour`() {
        val noTarget = input(targetWeightKg = null)
        val noWeeks = input(weeks = null)
        val invalidWeeks = input(weeks = 0)

        assertNull(DeadlinePlanner.feedback(OnboardingPlanner.plan(noTarget), noTarget, today))
        assertNull(DeadlinePlanner.feedback(OnboardingPlanner.plan(noWeeks), noWeeks, today))
        assertNull(DeadlinePlanner.feedback(OnboardingPlanner.plan(invalidWeeks), invalidWeeks, today))
    }

    @Test
    fun `a picked date rounds up to whole weeks and never yields zero`() {
        assertEquals(1, DeadlinePlanner.weeksUntil(today, today.plusDays(1)))
        assertEquals(1, DeadlinePlanner.weeksUntil(today, today.plusDays(7)))
        assertEquals(2, DeadlinePlanner.weeksUntil(today, today.plusDays(8)))
        assertEquals(1, DeadlinePlanner.weeksUntil(today, today.minusDays(14)))
    }
}
