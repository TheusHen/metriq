package com.metriq.core

import kotlin.math.abs

data class OnboardingInput(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: GoalType = GoalType.MAINTAIN,
    val diet: DietStyle = DietStyle.BALANCED,
    val targetWeightKg: Double? = null,
    val weeks: Int? = null,
)

data class PlanResult(
    val bmr: Double,
    val tdee: Double,
    val dailyKcal: Double,
    val proteinG: Int,
    val fatG: Int,
    val carbG: Int,
    val isMinor: Boolean,
    val warnings: List<DomainWarning>,
    val explanation: DomainWarning = DomainWarning(WarningCode.PLAN_EXPLANATION),
)

/**
 * Onboarding goal calculation. Adults go through the same equations used by the app
 * dashboard (Bmr.forProfile -> activity factor -> CaloriePlan.target -> MacroPlanner.plan).
 * Minors use a pediatric resting-energy equation. Every user-entered value is used
 * exactly as provided; unusual values only produce advisory warnings.
 *
 * All user-facing output is a [WarningCode] with plain params; no locale or language is
 * applied here.
 */
object OnboardingPlanner {

    private const val MINOR_AGE = 18

    private const val AGE_VALIDATED_MIN = 10
    private const val AGE_USUAL_MAX = 100

    // Typical ranges are advisory only: values outside them are still used as entered.
    private const val TYPICAL_HEIGHT_MIN_CM = 100.0
    private const val TYPICAL_HEIGHT_MAX_CM = 230.0
    private const val DEFAULT_HEIGHT_CM = 175.0

    private const val TYPICAL_WEIGHT_MIN_KG = 25.0
    private const val TYPICAL_WEIGHT_MAX_KG = 300.0
    private const val DEFAULT_WEIGHT_KG = 75.0

    private const val TYPICAL_BODY_FAT_MIN = 3.0
    private const val TYPICAL_BODY_FAT_MAX = 60.0

    private const val REFERENCE_ADULT_RATE_KG_PER_WEEK = 0.5
    private const val REFERENCE_MINOR_RATE_KG_PER_WEEK = 0.25

    private const val FAT_FLOOR_PER_KG = 0.5
    private const val MINOR_MAX_DEFICIT_PERCENT = 10

    /**
     * Schofield (1985) weight-only resting-energy equations for ages 10-18, as published
     * in WHO Technical Report Series 724 and reproduced in the "Schofield equation" table:
     * https://en.wikipedia.org/wiki/Schofield_equation (kcal/day, 10-18 years).
     * The equation is applied below age 18; ages outside 10-18 only add a warning.
     *   boys  17.686 kcal/kg/day + 658.2
     *   girls 13.384 kcal/kg/day + 692.6
     * Original reference: Schofield WN (1985), Hum Nutr Clin Nutr 39(Suppl 1):5-41.
     */
    private fun pediatricBmr(sex: Sex, weightKg: Double): Double {
        val bmr = when (sex) {
            Sex.MALE -> 17.686 * weightKg + 658.2
            Sex.FEMALE -> 13.384 * weightKg + 692.6
        }
        return bmr.coerceAtLeast(0.0)
    }

    fun plan(input: OnboardingInput): PlanResult {
        val warnings = ArrayList<DomainWarning>()

        // Age is accepted exactly as entered; only advisory codes change.
        val age = input.age
        if (age < AGE_VALIDATED_MIN) {
            warnings += DomainWarning.of(WarningCode.AGE_BELOW_PEDIATRIC_VALIDATION, "age" to age)
        } else if (age > AGE_USUAL_MAX) {
            warnings += DomainWarning.of(
                WarningCode.AGE_OUTSIDE_TYPICAL_RANGE,
                "age" to age, "min" to AGE_VALIDATED_MIN, "max" to AGE_USUAL_MAX,
            )
        }

        val heightCm = positiveOrFallback(
            input.heightCm, DEFAULT_HEIGHT_CM,
            WarningCode.HEIGHT_INVALID_FALLBACK, WarningCode.HEIGHT_OUTSIDE_TYPICAL_RANGE,
            warnings, TYPICAL_HEIGHT_MIN_CM, TYPICAL_HEIGHT_MAX_CM,
        )
        val weightKg = positiveOrFallback(
            input.weightKg, DEFAULT_WEIGHT_KG,
            WarningCode.WEIGHT_INVALID_FALLBACK, WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE,
            warnings, TYPICAL_WEIGHT_MIN_KG, TYPICAL_WEIGHT_MAX_KG,
        )
        val bodyFatPercent = optionalPositive(
            input.bodyFatPercent, WarningCode.BODY_FAT_INVALID,
            WarningCode.BODY_FAT_OUTSIDE_TYPICAL_RANGE, warnings,
            TYPICAL_BODY_FAT_MIN, TYPICAL_BODY_FAT_MAX,
        )
        val targetWeightKg = optionalPositive(
            input.targetWeightKg, WarningCode.TARGET_WEIGHT_INVALID,
            WarningCode.TARGET_WEIGHT_OUTSIDE_TYPICAL_RANGE, warnings,
            TYPICAL_WEIGHT_MIN_KG, TYPICAL_WEIGHT_MAX_KG,
        )
        val weeks = input.weeks?.takeIf { it >= 1 }
        if (input.weeks != null && weeks == null) {
            warnings += DomainWarning.of(WarningCode.WEEKS_INVALID, "minWeeks" to 1)
        }

        val isMinor = age < MINOR_AGE
        val profile = Profile(
            sex = input.sex,
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            activityLevel = input.activityLevel,
            goal = input.goal,
            diet = input.diet,
            proteinTier = ProteinTier.MODERATE,
        )

        var bmr = if (isMinor) pediatricBmr(input.sex, weightKg) else Bmr.forProfile(profile)
        if (!bmr.isFinite()) {
            // A physically impossible body-fat percentage can yield a negative fat-free
            // mass and a NaN composition equation; keep the maths finite without changing
            // any user-entered value outside of that sanity path.
            bmr = Bmr.fromBody(weightKg, heightCm, age, input.sex)
            warnings += DomainWarning(WarningCode.BODY_COMPOSITION_INCONSISTENT_BMR)
        }
        if (!bmr.isFinite()) bmr = 0.0
        bmr = bmr.coerceAtLeast(0.0)
        val tdee = bmr * input.activityLevel.factor

        // CONSERVATIVE (a 10% cut) is only the DEFAULT suggestion for a minor's CUT goal.
        // It is never used as a floor for a later explicit target.
        val aggressiveness = if (isMinor) Aggressiveness.CONSERVATIVE else Aggressiveness.MODERATE
        var dailyKcal = CaloriePlan.target(tdee, input.goal, aggressiveness).dailyKcal
        val plannedWeeks = weeks

        if (targetWeightKg != null && weeks != null) {
            val toLoseKg = weightKg - targetWeightKg
            val goalMismatch = (input.goal == GoalType.CUT && toLoseKg <= 0.0) ||
                (input.goal == GoalType.BULK && toLoseKg >= 0.0)
            if (goalMismatch) {
                warnings += DomainWarning.of(WarningCode.TARGET_WEIGHT_GOAL_MISMATCH, "goal" to input.goal.name)
            } else {
                val neededRate = abs(toLoseKg) / weeks
                val referenceRate = if (isMinor) {
                    REFERENCE_MINOR_RATE_KG_PER_WEEK
                } else {
                    REFERENCE_ADULT_RATE_KG_PER_WEEK
                }
                if (neededRate > referenceRate) {
                    warnings += rateWarning(neededRate, referenceRate)
                }
                val weeklyRateKg = toLoseKg / weeks
                dailyKcal = tdee - weeklyRateKg * Expenditure.KCAL_PER_KG / 7.0
            }
        } else if (targetWeightKg != null || input.weeks != null) {
            warnings += DomainWarning(WarningCode.TARGET_AND_WEEKS_REQUIRED)
        }

        if (isMinor) {
            warnings += DomainWarning.of(
                WarningCode.MINOR_DEFICIT_ADVISORY,
                "maxDeficitPercent" to MINOR_MAX_DEFICIT_PERCENT,
            )
            warnings += DomainWarning(WarningCode.NOT_MEDICAL_ADVICE)
            if (input.diet == DietStyle.KETO) {
                warnings += DomainWarning(WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS)
            }
        }

        if (!dailyKcal.isFinite()) dailyKcal = tdee
        if (dailyKcal < 0.0) {
            warnings += DomainWarning.of(WarningCode.NEGATIVE_KCAL_CLAMPED, "floorKcal" to 0)
            dailyKcal = 0.0
        }

        // The diet the user selected is honored, including for minors.
        val macros = MacroPlanner.plan(
            kcal = dailyKcal,
            bodyWeightKg = weightKg,
            fatFreeMassKg = profile.fatFreeMassKg,
            goal = input.goal,
            tier = ProteinTier.MODERATE,
            diet = input.diet,
        )
        if (macros.floorsRelaxed) {
            warnings += DomainWarning(WarningCode.MACRO_FLOORS_RELAXED)
        }

        val proteinFloorG = MacroPlanner.proteinPerKgFatFreeMass(input.goal, ProteinTier.MODERATE) *
            (profile.fatFreeMassKg ?: 0.75 * weightKg)
        if (dailyKcal < proteinFloorG * 4.0 + FAT_FLOOR_PER_KG * weightKg * 9.0) {
            warnings += DomainWarning.of(
                WarningCode.INSUFFICIENT_KCAL_FOR_FLOORS,
                "kcal" to dailyKcal, "proteinFloorG" to proteinFloorG,
                "fatFloorG" to FAT_FLOOR_PER_KG * weightKg,
            )
        }

        val explanationParams = LinkedHashMap<String, Any?>()
        explanationParams["mode"] = "ONBOARDING"
        explanationParams["weightKg"] = weightKg
        explanationParams["heightCm"] = heightCm
        explanationParams["age"] = age
        explanationParams["activity"] = input.activityLevel.name
        explanationParams["tdee"] = tdee
        explanationParams["goal"] = input.goal.name
        explanationParams["kcal"] = dailyKcal
        explanationParams["proteinG"] = macros.proteinG
        explanationParams["fatG"] = macros.fatG
        explanationParams["carbG"] = macros.carbG
        explanationParams["isMinor"] = isMinor
        if (plannedWeeks != null) explanationParams["weeks"] = plannedWeeks
        if (targetWeightKg != null) explanationParams["targetKg"] = targetWeightKg

        return PlanResult(
            bmr = bmr,
            tdee = tdee,
            dailyKcal = dailyKcal,
            proteinG = macros.proteinG.coerceAtLeast(0),
            fatG = macros.fatG.coerceAtLeast(0),
            carbG = macros.carbG.coerceAtLeast(0),
            isMinor = isMinor,
            warnings = warnings,
            explanation = DomainWarning.of(WarningCode.PLAN_EXPLANATION, explanationParams),
        )
    }

    private fun rateWarning(rate: Double, reference: Double): DomainWarning =
        DomainWarning.of(
            WarningCode.RATE_ABOVE_REFERENCE,
            "rateKgPerWeek" to rate,
            "referenceKgPerWeek" to reference,
        )

    private fun positiveOrFallback(
        value: Double,
        fallback: Double,
        invalidCode: WarningCode,
        typicalCode: WarningCode,
        warnings: MutableList<DomainWarning>,
        typicalMin: Double? = null,
        typicalMax: Double? = null,
    ): Double {
        if (!value.isFinite() || value <= 0.0) {
            warnings += DomainWarning.of(invalidCode, "fallback" to fallback)
            return fallback
        }
        warnIfOutsideTypical(value, warnings, typicalCode, typicalMin, typicalMax)
        return value
    }

    private fun optionalPositive(
        value: Double?,
        invalidCode: WarningCode,
        typicalCode: WarningCode,
        warnings: MutableList<DomainWarning>,
        typicalMin: Double? = null,
        typicalMax: Double? = null,
    ): Double? {
        if (value == null) return null
        if (!value.isFinite() || value <= 0.0) {
            warnings += DomainWarning(invalidCode)
            return null
        }
        warnIfOutsideTypical(value, warnings, typicalCode, typicalMin, typicalMax)
        return value
    }

    private fun warnIfOutsideTypical(
        value: Double,
        warnings: MutableList<DomainWarning>,
        code: WarningCode,
        typicalMin: Double?,
        typicalMax: Double?,
    ) {
        if ((typicalMin != null && value < typicalMin) || (typicalMax != null && value > typicalMax)) {
            warnings += DomainWarning.of(code, "value" to value, "min" to typicalMin, "max" to typicalMax)
        }
    }
}
