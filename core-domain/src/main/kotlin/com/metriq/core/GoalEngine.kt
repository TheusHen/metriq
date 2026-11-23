package com.metriq.core

import java.text.NumberFormat
import java.util.Locale
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
    val warnings: List<String>,
    val didClamp: Boolean,
    val summary: String,
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
 * The function is pure: no clock, I/O or Android APIs.
 */
object GoalEngine {

    private const val MINOR_AGE = 18
    private const val DEFAULT_WEIGHT_KG = 75.0

    private const val FAT_FLOOR_PER_KG = 0.5
    private const val ADULT_BMR_FACTOR = 1.10
    private const val MINOR_MAX_DEFICIT_SHARE = 0.10
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
        val hasDeadline = target != null && weeks != null

        val warnings = ArrayList<String>(suggested.warnings)
        warnings.addAll(rateWarnings(override, weightKg, minor))

        if (override.dailyKcal < 0) {
            warnings += "Meta negativa ignorada; ajustada para 0 kcal/dia."
        }
        if (!profile.weightKg.isFinite() || profile.weightKg <= 0.0) {
            warnings += "Peso inválido; usando ${number(DEFAULT_WEIGHT_KG)} kg como valor neutro."
        }
        if (profile.bodyFatPercent != null && bodyFat == null) {
            warnings += "Gordura corporal inválida; estimativa feita pelo peso corporal."
        }
        if (profile.bodyFatPercent != null && bodyFat != null && composedFfm != null && composedFfm <= 0.0) {
            warnings += "Composição corporal inconsistente; estimativa feita pelo peso corporal."
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
            if (warnings.none { it.contains("profissional de saúde") }) {
                warnings += "Este cálculo não substitui orientação de um responsável ou profissional de saúde."
            }
            if (profile.diet == DietStyle.KETO) {
                warnings += "Dietas cetogênicas não são indicadas para menores; acompanhe com um responsável/profissional."
            }
            if (appliedKcal < tdee * (1.0 - MINOR_MAX_DEFICIT_SHARE)) {
                warnings += "Deficit acima de 10% do gasto: acompanhe com responsavel/profissional."
            }
        } else if (appliedKcal < floorKcal) {
            warnings += "Meta abaixo do piso estimado de $floorKcal kcal/dia: " +
                "o cálculo substitui orientação profissional? Consulte um profissional de saúde."
        }

        if (appliedKcal.toDouble() < macroFloorKcal) {
            warnings += "Calorias insuficientes para proteína e gordura mínimas; carboidratos zerados."
        }
        override.reason?.takeIf { it.isNotBlank() }?.let { warnings += "Motivo informado: ${it.trim()}" }

        if (deadlineKcal == null && followKcal == null && requestedKcal == suggestedKcal) {
            return RecalculatedPlan(
                plan = suggested.copy(warnings = warnings),
                suggestedKcal = suggestedKcal,
                appliedKcal = suggestedKcal,
                warnings = warnings,
                didClamp = false,
                summary = "Meta mantida em ${number(suggestedKcal)} kcal/dia.",
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
            warnings += "Metas de proteína e gordura reduzidas para caber no alvo calórico."
        }

        val explanation = buildString {
            append(suggested.explanation)
            if (deadlineKcal != null && target != null && weeks != null) {
                append(" Meta recalculada de ${number(weightKg)} kg para ${number(target)} kg em $weeks semanas: ")
                append("${number(appliedKcal)} kcal/dia.")
            } else if (followKcal != null) {
                append(" Meta recalculada a partir do gasto atual: ${number(appliedKcal)} kcal/dia.")
            } else {
                append(" Meta ajustada manualmente para ${number(appliedKcal)} kcal/dia.")
            }
        }
        val planned = suggested.copy(
            dailyKcal = appliedKcal.toDouble(),
            proteinG = macros.proteinG.coerceAtLeast(0),
            fatG = macros.fatG.coerceAtLeast(0),
            carbG = macros.carbG.coerceAtLeast(0),
            warnings = warnings,
            explanation = explanation,
        )

        val proteinPhrase = if (macros.proteinG >= suggested.proteinG) "proteína mantida" else "proteína reduzida"
        val carbPhrase = when {
            macros.carbG < suggested.carbG -> "carboidrato reduzido"
            macros.carbG > suggested.carbG -> "carboidrato aumentado"
            else -> "carboidrato mantido"
        }
        val summary = "Meta ajustada de ${number(suggestedKcal)} para ${number(appliedKcal)} kcal/dia — " +
            "$proteinPhrase, $carbPhrase."

        return RecalculatedPlan(
            plan = planned,
            suggestedKcal = suggestedKcal,
            appliedKcal = appliedKcal,
            warnings = warnings,
            didClamp = didClamp,
            summary = summary,
        )
    }

    private fun rateWarnings(override: GoalOverride, weightKg: Double, isMinor: Boolean): List<String> {
        val result = ArrayList<String>()
        val target = override.targetWeightKg?.let { if (it.isFinite() && it > 0.0) it else null }
        val weeks = override.weeks?.takeIf { it >= 1 }

        if (override.targetWeightKg != null && target == null) {
            result += "Peso-alvo inválido; ajuste de taxa ignorado."
        }
        if (override.weeks != null && weeks == null) {
            result += "Prazo inválido; ajuste de taxa ignorado."
        }
        if (target == null || weeks == null) {
            if (override.targetWeightKg != null || override.weeks != null) {
                result += "Informe peso-alvo e prazo juntos; ajuste de taxa ignorado."
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
            result += "Ritmo solicitado ${number(neededRate, 2)} kg/semana acima do referencial de " +
                "${number(referenceRate, 2)} kg/semana; meta calculada com o prazo informado."
        }
        return result
    }

    private fun number(value: Double, digits: Int = 0): String {
        val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"))
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = digits
        return format.format(if (value.isFinite()) value else 0.0)
    }

    private fun number(value: Int, digits: Int = 0): String = number(value.toDouble(), digits)

    private fun safeDouble(value: Double, fallback: Double = 0.0): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else fallback

    private fun safeKcal(value: Double): Int {
        val safe = safeDouble(value, 0.0)
        return if (safe.isFinite() && safe <= Int.MAX_VALUE) Math.round(safe).toInt() else 0
    }
}
