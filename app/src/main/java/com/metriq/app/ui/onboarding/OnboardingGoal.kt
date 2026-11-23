package com.metriq.app.ui.onboarding

import com.metriq.core.GoalOverride
import com.metriq.core.PlanResult

/**
 * The reviewed onboarding plan is the user's first real goal: persist its daily kcal so the
 * dashboard keeps the same target after the wizard closes, together with any chosen deadline.
 */
internal fun buildOnboardingGoal(
    plan: PlanResult?,
    targetWeightKg: Double?,
    weeks: Int?,
): GoalOverride? {
    val dailyKcal = plan?.dailyKcal?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return GoalOverride(
        dailyKcal = Math.round(dailyKcal).toInt(),
        targetWeightKg = targetWeightKg,
        weeks = weeks,
        reason = "onboarding",
        // With a deadline the dashboard must re-derive kcal from current expenditure;
        // without one the reviewed onboarding number stays pinned.
        followExpenditure = targetWeightKg != null && weeks != null,
    )
}
