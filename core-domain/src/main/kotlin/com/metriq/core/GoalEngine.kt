package com.metriq.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** A manual kcal target (and optionally a weight deadline) requested by the user. */
data class GoalOverride(
    val dailyKcal: Int,
    val targetWeightKg: Double? = null,
    val weeks: Int? = null,
    val reason: String? = null,
    /** Recompute the goal from current expenditure instead of pinning [dailyKcal]. */
    val followExpenditure: Boolean = false,
)

/** Result of re-running the onboarding maths on top of a manual goal. */
data class RecalculatedPlan(
    val plan: PlanResult,
    val suggestedKcal: Int,
    val appliedKcal: Int,
    val warnings: List<DomainWarning>,
    val didClamp: Boolean,
    val summary: DomainWarning,
)

/**
 * Manual-goal recalculation using the same maths as [OnboardingPlanner].
 *
 * A valid target weight + deadline is authoritative: the required daily kcal is derived
 * from current expenditure and the weekly rate, then passed through a safety clamp.
 * Without a deadline the manual kcal is applied as entered (only a non-negative guard is
 * kept), unless [GoalOverride.followExpenditure] asks for a fresh expenditure-based goal.
 * Unsafe rates and floor breaches remain advisory warnings.
 *
 * The function is pure: no clock, I/O, Android APIs or locale/language formatting.
 */
object GoalEngine {

    private const val MINOR_AGE = 18
    private const val DEFAULT_WEIGHT_KG = 75.0

    private const val FAT_FLOOR_PER_KG = 0.5
    private const val ADULT_BMR_FACTOR = 1.10
    private const val MINOR_MAX_DEFICIT_PERCENT = 10
    private const val REFERENCE_ADULT_RATE_KG_PER_WEEK = 0.5
    private const val REFERENCE_MINOR_RATE_KG_PER_WEEK = 0.25
    private const val DEFAULT_ACTIVITY_FACTOR = 1.55
    private const val MAX_EXPENDITURE_FACTOR = 2.0

    fun recalculate(
        suggested: PlanResult,
        override: GoalOverride,
        profile: Profile,
        isMinor: Boolean,
    ): RecalculatedPlan {
        val suggestedKcal = safeKcal(suggested.dailyKcal)
        val requestedKcal = override.dailyKcal.coerceAtLeast(0)

        val weightKg = if (profile.weightKg.isFinite() && profile.weightKg > 0.0) {
            profile.weightKg
        } else {
            DEFAULT_WEIGHT_KG
        }
        val bodyFat = profile.bodyFatPercent?.let { if (it.isFinite() && it > 0.0) it else null }
        val fatMassKg = bodyFat?.let { weightKg * it / 100.0 }
        val composedFfm = fatMassKg?.let { weightKg - it }
        val fatFreeMassKg = if (composedFfm != null && composedFfm > 0.0) composedFfm else null

        // Only the caller-supplied minor flag (or a plainly underage profile age) drives
        // advisories. Age itself is never clamped or used to override the requested goal.
        val minor = isMinor || profile.age < MINOR_AGE
        val tdee = safeDouble(suggested.tdee, 0.0).let {
            if (it > 0.0) it else safeDouble(suggested.bmr, 0.0) * DEFAULT_ACTIVITY_FACTOR
        }
        val bmr = safeDouble(suggested.bmr, 0.0)

        val proteinFloorG = MacroPlanner.proteinPerKgFatFreeMass(profile.goal, ProteinTier.MODERATE) *
            (fatFreeMassKg ?: 0.75 * weightKg)
        val fatFloorG = FAT_FLOOR_PER_KG * weightKg
        val macroFloorKcal = proteinFloorG * 4.0 + fatFloorG * 9.0
        val adultFloorKcal = maxOf(bmr * ADULT_BMR_FACTOR, macroFloorKcal)
        val floorKcal = if (adultFloorKcal.isFinite()) ceil(adultFloorKcal).toInt().coerceAtLeast(0) else 0

        val target = override.targetWeightKg?.let { if (it.isFinite() && it > 0.0) it else null }
        val weeks = override.weeks?.takeIf { it >= 1 }

        val warnings = ArrayList<DomainWarning>(suggested.warnings)
        warnings.addAll(rateWarnings(override, weightKg, minor))

        if (override.dailyKcal < 0) {
            warnings += DomainWarning.of(WarningCode.NEGATIVE_KCAL_CLAMPED, "floorKcal" to 0)
        }
        if (!profile.weightKg.isFinite() || profile.weightKg <= 0.0) {
            warnings += DomainWarning.of(WarningCode.WEIGHT_INVALID_FALLBACK, "fallback" to DEFAULT_WEIGHT_KG)
        }
        if (profile.bodyFatPercent != null && bodyFat == null) {
            warnings += DomainWarning(WarningCode.BODY_FAT_INVALID_USING_WEIGHT)
        }
        if (profile.bodyFatPercent != null && bodyFat != null && composedFfm != null && composedFfm <= 0.0) {
            warnings += DomainWarning(WarningCode.BODY_COMPOSITION_INCONSISTENT_USING_WEIGHT)
        }

        var didClamp = false
        val upperKcal = max(0, ceil(maxOf(tdee, bmr) * MAX_EXPENDITURE_FACTOR).toInt())
        fun clamp(raw: Int): Int {
            val clamped = raw.coerceIn(0, upperKcal)
            if (clamped != raw) didClamp = true
            return clamped
        }

        // A valid deadline is authoritative: rate * 7716 kcal/kg spread over 7 days.
        val deadlineKcal = if (target != null && weeks != null) {
            val weeklyRateKg = abs(weightKg - target) / weeks.toDouble()
            val signedRate = if (target < weightKg) -weeklyRateKg else weeklyRateKg
            val raw = tdee + signedRate * Expenditure.KCAL_PER_KG / 7.0
            clamp(if (raw.isFinite()) Math.round(raw).toInt() else suggestedKcal)
        } else {
            null
        }

        // No deadline: optionally rebuild the goal from current expenditure instead of
        // honoring the pinned kcal. The suggested plan's goal aggressiveness is kept.
        val followKcal = if (deadlineKcal == null && override.followExpenditure) {
            val aggressiveness = if (minor) Aggressiveness.CONSERVATIVE else Aggressiveness.MODERATE
            val raw = CaloriePlan.target(tdee, profile.goal, aggressiveness).dailyKcal
            clamp(if (raw.isFinite()) Math.round(raw).toInt() else suggestedKcal)
        } else {
            null
        }

        val appliedKcal = deadlineKcal ?: followKcal ?: requestedKcal

        if (minor) {
            if (warnings.none { it.code == WarningCode.NOT_MEDICAL_ADVICE }) {
                warnings += DomainWarning(WarningCode.NOT_MEDICAL_ADVICE)
            }
            if (profile.diet == DietStyle.KETO) {
                warnings += DomainWarning(WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS)
            }
            if (appliedKcal < tdee * (1.0 - MINOR_MAX_DEFICIT_PERCENT / 100.0)) {
                warnings += DomainWarning.of(
                    WarningCode.MINOR_DEFICIT_ABOVE_LIMIT,
                    "limitPercent" to MINOR_MAX_DEFICIT_PERCENT,
                    "tdee" to tdee,
                    "appliedKcal" to appliedKcal,
                )
            }
        } else if (appliedKcal < floorKcal) {
            warnings += DomainWarning.of(
                WarningCode.KCAL_BELOW_FLOOR,
                "floorKcal" to floorKcal,
                "appliedKcal" to appliedKcal,
            )
        }

        if (appliedKcal.toDouble() < macroFloorKcal) {
            warnings += DomainWarning.of(
                WarningCode.INSUFFICIENT_KCAL_FOR_FLOORS,
                "kcal" to appliedKcal,
                "proteinFloorG" to proteinFloorG,
                "fatFloorG" to fatFloorG,
            )
        }
        override.reason?.takeIf { it.isNotBlank() }?.let {
            warnings += DomainWarning.of(WarningCode.GOAL_REASON, "reason" to it.trim())
        }

        if (deadlineKcal == null && followKcal == null && requestedKcal == suggestedKcal) {
            return RecalculatedPlan(
                plan = suggested.copy(warnings = warnings),
                suggestedKcal = suggestedKcal,
                appliedKcal = suggestedKcal,
                warnings = warnings,
                didClamp = false,
                summary = DomainWarning.of(WarningCode.SUMMARY_GOAL_UNCHANGED, "kcal" to suggestedKcal),
            )
        }

        val macros = MacroPlanner.plan(
            kcal = appliedKcal.toDouble(),
            bodyWeightKg = weightKg,
            fatFreeMassKg = fatFreeMassKg,
            goal = profile.goal,
            tier = ProteinTier.MODERATE,
            diet = profile.diet,
        )
        if (macros.floorsRelaxed) {
            warnings += DomainWarning(WarningCode.MACRO_FLOORS_RELAXED)
        }

        val explanationParams = LinkedHashMap<String, Any?>(suggested.explanation.params)
        when {
            deadlineKcal != null && target != null && weeks != null -> {
                explanationParams["mode"] = "DEADLINE"
                explanationParams["fromKg"] = weightKg
                explanationParams["toKg"] = target
                explanationParams["weeks"] = weeks
            }
            followKcal != null -> explanationParams["mode"] = "FOLLOW_EXPENDITURE"
            else -> explanationParams["mode"] = "MANUAL"
        }
        explanationParams["kcal"] = appliedKcal

        val planned = suggested.copy(
            dailyKcal = appliedKcal.toDouble(),
            proteinG = macros.proteinG.coerceAtLeast(0),
            fatG = macros.fatG.coerceAtLeast(0),
            carbG = macros.carbG.coerceAtLeast(0),
            warnings = warnings,
            explanation = DomainWarning.of(WarningCode.PLAN_EXPLANATION, explanationParams),
        )

        val proteinChange = if (macros.proteinG >= suggested.proteinG) "MAINTAINED" else "REDUCED"
        val carbChange = when {
            macros.carbG < suggested.carbG -> "REDUCED"
            macros.carbG > suggested.carbG -> "INCREASED"
            else -> "MAINTAINED"
        }
        val summary = DomainWarning.of(
            WarningCode.SUMMARY_GOAL_ADJUSTED,
            "fromKcal" to suggestedKcal,
            "toKcal" to appliedKcal,
            "proteinChange" to proteinChange,
            "carbChange" to carbChange,
        )

        return RecalculatedPlan(
            plan = planned,
            suggestedKcal = suggestedKcal,
            appliedKcal = appliedKcal,
            warnings = warnings,
            didClamp = didClamp,
            summary = summary,
        )
    }

    private fun rateWarnings(override: GoalOverride, weightKg: Double, isMinor: Boolean): List<DomainWarning> {
        val result = ArrayList<DomainWarning>()
        val target = override.targetWeightKg?.let { if (it.isFinite() && it > 0.0) it else null }
        val weeks = override.weeks?.takeIf { it >= 1 }

        if (override.targetWeightKg != null && target == null) {
            result += DomainWarning(WarningCode.TARGET_WEIGHT_INVALID)
        }
        if (override.weeks != null && weeks == null) {
            result += DomainWarning.of(WarningCode.WEEKS_INVALID, "minWeeks" to 1)
        }
        if (target == null || weeks == null) {
            if (override.targetWeightKg != null || override.weeks != null) {
                result += DomainWarning(WarningCode.TARGET_AND_WEEKS_REQUIRED)
            }
            return result
        }

        val deltaKg = abs(weightKg - target)
        val neededRate = deltaKg / weeks
        val referenceRate = if (isMinor) {
            REFERENCE_MINOR_RATE_KG_PER_WEEK
        } else {
            REFERENCE_ADULT_RATE_KG_PER_WEEK
        }
        if (neededRate > referenceRate) {
            result += DomainWarning.of(
                WarningCode.RATE_ABOVE_REFERENCE,
                "rateKgPerWeek" to neededRate,
                "referenceKgPerWeek" to referenceRate,
            )
        }
        return result
    }

    private fun safeDouble(value: Double, fallback: Double = 0.0): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else fallback

    private fun safeKcal(value: Double): Int {
        val safe = safeDouble(value, 0.0)
        return if (safe.isFinite() && safe <= Int.MAX_VALUE) Math.round(safe).toInt() else 0
    }
}
