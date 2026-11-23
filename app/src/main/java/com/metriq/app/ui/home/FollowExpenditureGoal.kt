package com.metriq.app.ui.home

import com.metriq.core.Expenditure
import com.metriq.core.GoalOverride
import kotlin.math.round

/**
 * Recomputes a "follow expenditure" goal from the latest device-derived expenditure.
 *
 * A fixed kcal goal pinned at onboarding defeats any later improvement in the
 * expenditure estimate. When [GoalOverride.followExpenditure] is set, the stored target
 * weight and deadline are re-run through the same rate math used by OnboardingPlanner
 * (`tdee - weeklyRateKg * KCAL_PER_KG / 7`) against the CURRENT expenditure. Manual goals
 * are left untouched.
 *
 * Pure and side-effect free: [resolve] either returns the original instance or an
 * in-memory copy, so the persisted JSON is never mutated.
 */
internal object FollowExpenditureGoal {

    fun resolve(
        stored: GoalOverride,
        currentExpenditureKcal: Double,
        currentWeightKg: Double,
    ): GoalOverride {
        if (!stored.followExpenditure) return stored
        val kcal = recomputeDailyKcal(
            currentExpenditureKcal = currentExpenditureKcal,
            currentWeightKg = currentWeightKg,
            targetWeightKg = stored.targetWeightKg,
            weeks = stored.weeks,
        ) ?: return stored
        return stored.copy(dailyKcal = kcal)
    }

    /**
     * Signed rate math, identical to OnboardingPlanner: losing weight (target below
     * current) lowers kcal, gaining weight raises it. Returns null when the target or
     * deadline is missing/invalid, so callers keep the stored goal in that case.
     */
    fun recomputeDailyKcal(
        currentExpenditureKcal: Double,
        currentWeightKg: Double,
        targetWeightKg: Double?,
        weeks: Int?,
    ): Int? {
        if (targetWeightKg == null || !targetWeightKg.isFinite() || targetWeightKg <= 0.0) return null
        if (weeks == null || weeks < 1) return null
        if (!currentExpenditureKcal.isFinite() || currentExpenditureKcal <= 0.0) return null
        if (!currentWeightKg.isFinite() || currentWeightKg <= 0.0) return null

        val weeklyRateKg = (currentWeightKg - targetWeightKg) / weeks
        val raw = currentExpenditureKcal - weeklyRateKg * Expenditure.KCAL_PER_KG / 7.0
        if (!raw.isFinite()) return null
        return round(raw).toInt().coerceAtLeast(0)
    }
}
