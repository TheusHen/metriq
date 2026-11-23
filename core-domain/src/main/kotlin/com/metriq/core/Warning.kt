package com.metriq.core

/**
 * Stable identifiers for every message the domain can emit.
 *
 * Nothing in `core-domain` is localized: each entry is a UPPER_SNAKE code plus plain
 * values in [DomainWarning.params]. The app layer maps a code to a translated string
 * and formats any numeric params with the user's locale.
 *
 * The enum also covers the two narrative messages (plan explanation and goal summary)
 * and the training-adaptation reason: all user-facing text lives here so the JVM domain
 * has no hardcoded language.
 */
enum class WarningCode {
    // ---- Input validation / advisory -------------------------------------------------
    /** params: age */
    AGE_BELOW_PEDIATRIC_VALIDATION,
    /** params: age, min, max */
    AGE_OUTSIDE_TYPICAL_RANGE,
    /** params: fallback */
    HEIGHT_INVALID_FALLBACK,
    /** params: value, min, max */
    HEIGHT_OUTSIDE_TYPICAL_RANGE,
    /** params: fallback */
    WEIGHT_INVALID_FALLBACK,
    /** params: value, min, max */
    WEIGHT_OUTSIDE_TYPICAL_RANGE,
    /** no params */
    BODY_FAT_INVALID,
    /** no params */
    BODY_FAT_INVALID_USING_WEIGHT,
    /** params: value, min, max */
    BODY_FAT_OUTSIDE_TYPICAL_RANGE,
    /** no params */
    TARGET_WEIGHT_INVALID,
    /** params: value, min, max */
    TARGET_WEIGHT_OUTSIDE_TYPICAL_RANGE,
    /** params: minWeeks */
    WEEKS_INVALID,
    /** no params */
    TARGET_AND_WEEKS_REQUIRED,
    /** no params */
    BODY_COMPOSITION_INCONSISTENT_BMR,
    /** no params */
    BODY_COMPOSITION_INCONSISTENT_USING_WEIGHT,

    // ---- Goal / rate / safety --------------------------------------------------------
    /** params: goal */
    TARGET_WEIGHT_GOAL_MISMATCH,
    /** params: rateKgPerWeek, referenceKgPerWeek */
    RATE_ABOVE_REFERENCE,
    /** params: floorKcal */
    NEGATIVE_KCAL_CLAMPED,
    /** params: floorKcal, appliedKcal */
    KCAL_BELOW_FLOOR,
    /** params: limitPercent, tdee, appliedKcal */
    MINOR_DEFICIT_ABOVE_LIMIT,
    /** params: maxDeficitPercent */
    MINOR_DEFICIT_ADVISORY,
    /** no params */
    NOT_MEDICAL_ADVICE,
    /** no params */
    KETO_NOT_RECOMMENDED_FOR_MINORS,
    /** params: kcal, proteinFloorG, fatFloorG */
    INSUFFICIENT_KCAL_FOR_FLOORS,
    /** no params */
    MACRO_FLOORS_RELAXED,
    /** params: reason */
    GOAL_REASON,

    // ---- Narrative -------------------------------------------------------------------
    /**
     * params: mode (ONBOARDING | DEADLINE | FOLLOW_EXPENDITURE | MANUAL), weightKg,
     * heightCm, age, activity, tdee, goal, kcal, proteinG, fatG, carbG, isMinor,
     * and optionally weeks, targetKg, fromKg, toKg.
     */
    PLAN_EXPLANATION,
    /** params: kcal */
    SUMMARY_GOAL_UNCHANGED,
    /** params: fromKcal, toKcal, proteinChange, carbChange */
    SUMMARY_GOAL_ADJUSTED,

    // ---- Training adaptation ---------------------------------------------------------
    /** no params */
    MACRO_ADAPTATION_DISABLED,
    /** no params */
    MACRO_SIGNAL_ADAPTATION_DISABLED,
    /** no params */
    NO_TRAINING_SIGNAL,
    /** no params */
    KETO_NO_CARB_ADJUSTMENT,
    /** no params */
    NO_FAT_MARGIN,
    /** params: intensity, carbDeltaG, fatDeltaG, overreaching, and (signal variant) minutes, muscles, volumeKg */
    CARB_REALLOCATED,
}

/**
 * One locale-neutral domain message. [params] values are plain strings produced by
 * `toString()` on the original value (e.g. "0.5", "175"), never locale-formatted.
 */
data class DomainWarning(
    val code: WarningCode,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Convenience builder: numeric and enum values are stringified without a locale. */
        fun of(code: WarningCode, vararg params: Pair<String, Any?>): DomainWarning =
            of(code, params.toMap())

        /** Builds a warning from a map of plain (unformatted) values. */
        fun of(code: WarningCode, params: Map<String, Any?>): DomainWarning = DomainWarning(
            code = code,
            params = params.mapValues { (_, value) -> stringify(value) },
        )

        private fun stringify(value: Any?): String = when (value) {
            is Double -> if (value.isFinite() && value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString()
            }
            is Float -> if (value.isFinite() && value % 1.0f == 0.0f) {
                value.toLong().toString()
            } else {
                value.toString()
            }
            else -> value?.toString().orEmpty()
        }
    }
}
