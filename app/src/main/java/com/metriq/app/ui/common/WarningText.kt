package com.metriq.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metriq.app.R
import com.metriq.core.DomainWarning
import com.metriq.core.WarningCode
import java.util.Locale

/**
 * A warning template resolved to one string resource plus arguments that are already
 * formatted with the user's locale. Keeping this step pure makes the locale contract
 * testable on the JVM without a Compose runtime.
 */
internal data class ResolvedWarning(val resId: Int, val args: List<Any>)

/**
 * Single render site for every domain warning. Numeric params arrive as locale-neutral
 * strings from core-domain, so they are parsed and formatted with [Locale.getDefault()];
 * enum params are mapped to translated labels instead of leaking `CUT`/`MODERATE`.
 */
@Composable
fun warningText(warning: DomainWarning): String {
    val context = LocalContext.current
    val resolved = resolveWarning(warning, Locale.getDefault()) { context.getString(it) }
    return stringResource(resolved.resId, *resolved.args.toTypedArray()).trim()
}

/**
 * Maps every [WarningCode] to a translated resource. [text] resolves a raw resource
 * template; [locale] formats every numeric param. No locale is hardcoded here.
 */
internal fun resolveWarning(
    warning: DomainWarning,
    locale: Locale,
    text: (Int) -> String,
): ResolvedWarning {
    fun raw(key: String): String = warning.params[key].orEmpty()

    fun number(key: String): String = formatWarningNumber(raw(key), locale)

    fun signed(key: String): String {
        val value = number(key)
        return if (value.startsWith('-')) value else "+$value"
    }

    fun labelOf(key: String, vararg candidates: Pair<String, Int>): String {
        val value = raw(key)
        return candidates.firstOrNull { it.first.equals(value, ignoreCase = true) }
            ?.let { text(it.second) } ?: value
    }

    fun fmt(resId: Int, vararg args: Any): String = String.format(locale, text(resId), *args)

    fun explanationDetail(): String {
        val from = raw("fromKg")
        val to = raw("toKg")
        val weeks = raw("weeks")
        return when {
            from.isNotBlank() && to.isNotBlank() && weeks.isNotBlank() ->
                fmt(R.string.warning_deadline_detail, number("fromKg"), number("toKg"), number("weeks"))
            raw("targetKg").isNotBlank() -> fmt(R.string.warning_target_detail, number("targetKg"))
            weeks.isNotBlank() -> fmt(R.string.warning_weeks_detail, number("weeks"))
            else -> ""
        }
    }

    fun adaptationDetail(): String = buildList {
        if (raw("overreaching").equals("true", ignoreCase = true)) {
            add(text(R.string.warning_overreaching))
        }
        val minutes = raw("minutes")
        val muscles = raw("muscles")
        val volume = raw("volumeKg")
        if (minutes.isNotBlank() && muscles.isNotBlank() && volume.isNotBlank()) {
            add(
                fmt(
                    R.string.warning_signal_detail,
                    number("minutes"), number("muscles"), number("volumeKg"),
                ),
            )
        }
    }.joinToString(" ")

    val args: List<Any> = when (warning.code) {
        WarningCode.AGE_BELOW_PEDIATRIC_VALIDATION -> listOf(number("age"))
        WarningCode.AGE_OUTSIDE_TYPICAL_RANGE ->
            listOf(number("age"), number("min"), number("max"))
        WarningCode.HEIGHT_INVALID_FALLBACK -> listOf(number("fallback"))
        WarningCode.HEIGHT_OUTSIDE_TYPICAL_RANGE ->
            listOf(number("value"), number("min"), number("max"))
        WarningCode.WEIGHT_INVALID_FALLBACK -> listOf(number("fallback"))
        WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE ->
            listOf(number("value"), number("min"), number("max"))
        WarningCode.BODY_FAT_INVALID -> emptyList()
        WarningCode.BODY_FAT_INVALID_USING_WEIGHT -> emptyList()
        WarningCode.BODY_FAT_OUTSIDE_TYPICAL_RANGE ->
            listOf(number("value"), number("min"), number("max"))
        WarningCode.TARGET_WEIGHT_INVALID -> emptyList()
        WarningCode.TARGET_WEIGHT_OUTSIDE_TYPICAL_RANGE ->
            listOf(number("value"), number("min"), number("max"))
        WarningCode.WEEKS_INVALID -> listOf(number("minWeeks"))
        WarningCode.TARGET_AND_WEEKS_REQUIRED -> emptyList()
        WarningCode.BODY_COMPOSITION_INCONSISTENT_BMR -> emptyList()
        WarningCode.BODY_COMPOSITION_INCONSISTENT_USING_WEIGHT -> emptyList()
        WarningCode.TARGET_WEIGHT_GOAL_MISMATCH -> listOf(
            labelOf(
                "goal",
                "CUT" to R.string.warning_goal_cut,
                "MAINTAIN" to R.string.warning_goal_maintain,
                "BULK" to R.string.warning_goal_bulk,
            ),
        )
        WarningCode.RATE_ABOVE_REFERENCE ->
            listOf(number("rateKgPerWeek"), number("referenceKgPerWeek"))
        WarningCode.NEGATIVE_KCAL_CLAMPED -> listOf(number("floorKcal"))
        WarningCode.KCAL_BELOW_FLOOR ->
            listOf(number("floorKcal"), number("appliedKcal"))
        WarningCode.MINOR_DEFICIT_ABOVE_LIMIT ->
            listOf(number("limitPercent"), number("tdee"), number("appliedKcal"))
        WarningCode.MINOR_DEFICIT_ADVISORY -> listOf(number("maxDeficitPercent"))
        WarningCode.NOT_MEDICAL_ADVICE -> emptyList()
        WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS -> emptyList()
        WarningCode.INSUFFICIENT_KCAL_FOR_FLOORS ->
            listOf(number("kcal"), number("proteinFloorG"), number("fatFloorG"))
        WarningCode.MACRO_FLOORS_RELAXED -> emptyList()
        WarningCode.GOAL_REASON -> listOf(
            if (raw("reason").equals("onboarding", ignoreCase = true)) {
                text(R.string.warning_reason_onboarding)
            } else {
                raw("reason")
            },
        )
        WarningCode.PLAN_EXPLANATION -> listOf(
            number("weightKg"),
            number("heightCm"),
            number("age"),
            labelOf(
                "activity",
                "SEDENTARY" to R.string.warning_activity_sedentary,
                "LIGHT" to R.string.warning_activity_light,
                "MODERATE" to R.string.warning_activity_moderate,
                "HIGH" to R.string.warning_activity_high,
                "ATHLETE" to R.string.warning_activity_athlete,
            ),
            number("tdee"),
            labelOf(
                "goal",
                "CUT" to R.string.warning_goal_cut,
                "MAINTAIN" to R.string.warning_goal_maintain,
                "BULK" to R.string.warning_goal_bulk,
            ),
            number("kcal"),
            number("proteinG"),
            number("fatG"),
            number("carbG"),
            if (raw("isMinor").equals("true", ignoreCase = true)) {
                text(R.string.warning_minor)
            } else {
                text(R.string.warning_adult)
            },
            explanationDetail(),
        )
        WarningCode.SUMMARY_GOAL_UNCHANGED -> listOf(number("kcal"))
        WarningCode.SUMMARY_GOAL_ADJUSTED -> listOf(
            number("fromKcal"),
            number("toKcal"),
            labelOf(
                "proteinChange",
                "MAINTAINED" to R.string.warning_change_maintained,
                "REDUCED" to R.string.warning_change_reduced,
                "INCREASED" to R.string.warning_change_increased,
            ),
            labelOf(
                "carbChange",
                "MAINTAINED" to R.string.warning_change_maintained,
                "REDUCED" to R.string.warning_change_reduced,
                "INCREASED" to R.string.warning_change_increased,
            ),
        )
        WarningCode.MACRO_ADAPTATION_DISABLED -> emptyList()
        WarningCode.MACRO_SIGNAL_ADAPTATION_DISABLED -> emptyList()
        WarningCode.NO_TRAINING_SIGNAL -> emptyList()
        WarningCode.KETO_NO_CARB_ADJUSTMENT -> emptyList()
        WarningCode.NO_FAT_MARGIN -> emptyList()
        WarningCode.CARB_REALLOCATED -> listOf(
            labelOf(
                "intensity",
                "REST" to R.string.warning_intensity_rest,
                "LIGHT" to R.string.warning_intensity_light,
                "MODERATE" to R.string.warning_intensity_moderate,
                "HARD" to R.string.warning_intensity_hard,
            ),
            signed("carbDeltaG"),
            signed("fatDeltaG"),
            adaptationDetail(),
        )
    }

    val resId = when (warning.code) {
        WarningCode.AGE_BELOW_PEDIATRIC_VALIDATION -> R.string.warning_age_below_pediatric
        WarningCode.AGE_OUTSIDE_TYPICAL_RANGE -> R.string.warning_age_outside_typical
        WarningCode.HEIGHT_INVALID_FALLBACK -> R.string.warning_height_invalid_fallback
        WarningCode.HEIGHT_OUTSIDE_TYPICAL_RANGE -> R.string.warning_height_outside_typical
        WarningCode.WEIGHT_INVALID_FALLBACK -> R.string.warning_weight_invalid_fallback
        WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE -> R.string.warning_weight_outside_typical
        WarningCode.BODY_FAT_INVALID -> R.string.warning_body_fat_invalid
        WarningCode.BODY_FAT_INVALID_USING_WEIGHT -> R.string.warning_body_fat_invalid_using_weight
        WarningCode.BODY_FAT_OUTSIDE_TYPICAL_RANGE -> R.string.warning_body_fat_outside_typical
        WarningCode.TARGET_WEIGHT_INVALID -> R.string.warning_target_weight_invalid
        WarningCode.TARGET_WEIGHT_OUTSIDE_TYPICAL_RANGE -> R.string.warning_target_weight_outside_typical
        WarningCode.WEEKS_INVALID -> R.string.warning_weeks_invalid
        WarningCode.TARGET_AND_WEEKS_REQUIRED -> R.string.warning_target_and_weeks_required
        WarningCode.BODY_COMPOSITION_INCONSISTENT_BMR -> R.string.warning_body_comp_inconsistent_bmr
        WarningCode.BODY_COMPOSITION_INCONSISTENT_USING_WEIGHT -> R.string.warning_body_comp_inconsistent_weight
        WarningCode.TARGET_WEIGHT_GOAL_MISMATCH -> R.string.warning_target_goal_mismatch
        WarningCode.RATE_ABOVE_REFERENCE -> R.string.warning_rate_above_reference
        WarningCode.NEGATIVE_KCAL_CLAMPED -> R.string.warning_negative_kcal_clamped
        WarningCode.KCAL_BELOW_FLOOR -> R.string.warning_kcal_below_floor
        WarningCode.MINOR_DEFICIT_ABOVE_LIMIT -> R.string.warning_minor_deficit_above_limit
        WarningCode.MINOR_DEFICIT_ADVISORY -> R.string.warning_minor_deficit_advisory
        WarningCode.NOT_MEDICAL_ADVICE -> R.string.warning_not_medical_advice
        WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS -> R.string.warning_keto_not_recommended_minors
        WarningCode.INSUFFICIENT_KCAL_FOR_FLOORS -> R.string.warning_insufficient_kcal_floors
        WarningCode.MACRO_FLOORS_RELAXED -> R.string.warning_macro_floors_relaxed
        WarningCode.GOAL_REASON -> R.string.warning_goal_reason
        WarningCode.PLAN_EXPLANATION -> R.string.warning_plan_explanation
        WarningCode.SUMMARY_GOAL_UNCHANGED -> R.string.warning_summary_unchanged
        WarningCode.SUMMARY_GOAL_ADJUSTED -> R.string.warning_summary_adjusted
        WarningCode.MACRO_ADAPTATION_DISABLED -> R.string.warning_macro_adaptation_disabled
        WarningCode.MACRO_SIGNAL_ADAPTATION_DISABLED -> R.string.warning_macro_signal_disabled
        WarningCode.NO_TRAINING_SIGNAL -> R.string.warning_no_training_signal
        WarningCode.KETO_NO_CARB_ADJUSTMENT -> R.string.warning_keto_no_carb_adjustment
        WarningCode.NO_FAT_MARGIN -> R.string.warning_no_fat_margin
        WarningCode.CARB_REALLOCATED -> R.string.warning_carb_reallocated
    }
    return ResolvedWarning(resId, args)
}

/** "0.5" -> "0,5" in pt-BR and "0.5" in en-US; integral values keep no decimals. */
private fun formatWarningNumber(raw: String, locale: Locale): String {
    val value = raw.toDoubleOrNull() ?: return raw
    if (!value.isFinite()) return raw
    return if (value % 1.0 == 0.0) {
        String.format(locale, "%,.0f", value)
    } else {
        String.format(locale, "%,.2f", value).trimEnd('0').trimEnd('.', ',')
    }
}
