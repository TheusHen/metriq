package com.metriq.app.ui.onboarding

import com.metriq.core.GoalType
import com.metriq.core.OnboardingInput
import com.metriq.core.OnboardingPlanner
import com.metriq.core.PlanResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a chosen deadline compares with the safe reference rate. */
internal enum class DeadlineAssessment { NONE, ON_TRACK, AGGRESSIVE, INCOMPATIBLE }

/**
 * Live, read-only view of a deadline. [dailyKcal] is taken verbatim from the
 * [OnboardingPlanner] result — this type never repeats the energy maths.
 */
internal data class DeadlineFeedback(
    val dailyKcal: Int,
    val kgPerWeek: Double,
    val referenceKgPerWeek: Double,
    val targetDate: LocalDate,
    val assessment: DeadlineAssessment,
) {
    val hasDeadline: Boolean get() = assessment != DeadlineAssessment.NONE
    val isAboveSafe: Boolean get() = assessment == DeadlineAssessment.AGGRESSIVE
    val isRealistic: Boolean get() = assessment == DeadlineAssessment.ON_TRACK
}

/**
 * Derives deadline feedback from the plan the wizard already computed. The only maths
 * here is the calendar/rate arithmetic the planner does not expose; kcal always comes
 * from [plan], so a single domain function remains the source of truth.
 */
internal object DeadlinePlanner {

    // Published safe references, also used by the domain planner for its warnings.
    const val ADULT_REFERENCE_KG_PER_WEEK = 0.5
    const val MINOR_REFERENCE_KG_PER_WEEK = 0.25
    private const val MIN_WEEKS = 1

    fun feedback(
        plan: PlanResult,
        input: OnboardingInput,
        today: LocalDate = LocalDate.now(),
    ): DeadlineFeedback? {
        val target = input.targetWeightKg?.takeIf { it.isFinite() && it > 0.0 }
        val weeks = input.weeks?.takeIf { it >= MIN_WEEKS } ?: return null
        if (target == null) return null

        val reference = if (plan.isMinor) MINOR_REFERENCE_KG_PER_WEEK else ADULT_REFERENCE_KG_PER_WEEK
        val kgPerWeek = (input.weightKg - target) / weeks
        val mismatch = (input.goal == GoalType.CUT && target >= input.weightKg) ||
            (input.goal == GoalType.BULK && target <= input.weightKg)
        val assessment = when {
            mismatch -> DeadlineAssessment.INCOMPATIBLE
            abs(kgPerWeek) > reference -> DeadlineAssessment.AGGRESSIVE
            else -> DeadlineAssessment.ON_TRACK
        }
        return DeadlineFeedback(
            dailyKcal = plan.dailyKcal.roundToInt(),
            kgPerWeek = kgPerWeek,
            referenceKgPerWeek = reference,
            targetDate = today.plusWeeks(weeks.toLong()),
            assessment = assessment,
        )
    }

    /** A picked date becomes whole weeks, rounding up so any started week counts. */
    fun weeksUntil(today: LocalDate, deadline: LocalDate): Int =
        ((ChronoUnit.DAYS.between(today, deadline) + 6) / 7).toInt().coerceAtLeast(MIN_WEEKS)
}
