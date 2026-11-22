package com.metriq.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

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
    val warnings: List<String>,
    val explanation: String,
)

/**
 * Onboarding goal calculation. Adults go through the same equations used by the app
 * dashboard (Bmr.forProfile -> activity factor -> CaloriePlan.target -> MacroPlanner.plan).
 * Minors use a pediatric resting-energy equation and stricter safety guardrails.
 */
object OnboardingPlanner {

    private const val MIN_AGE = 14
    private const val MAX_AGE = 100
    private const val MINOR_AGE = 18

    private const val MIN_HEIGHT_CM = 100.0
    private const val MAX_HEIGHT_CM = 230.0
    private const val DEFAULT_HEIGHT_CM = 175.0

    private const val MIN_WEIGHT_KG = 25.0
    private const val MAX_WEIGHT_KG = 300.0
    private const val DEFAULT_WEIGHT_KG = 75.0

    private const val MIN_BODY_FAT = 3.0
    private const val MAX_BODY_FAT = 60.0
    private const val LOW_BODY_FAT_KETO_HINT = 12.0

    private const val MAX_ADULT_RATE_KG_PER_WEEK = 0.5
    private const val MAX_MINOR_RATE_KG_PER_WEEK = 0.25
    private const val MINOR_MAX_DEFICIT_SHARE = 0.10

    private const val FAT_FLOOR_PER_KG = 0.5

    /**
     * Schofield (1985) weight-only resting-energy equations for ages 10-18, as published
     * in WHO Technical Report Series 724 and reproduced in the "Schofield equation" table:
     * https://en.wikipedia.org/wiki/Schofield_equation (kcal/day, 10-18 years).
     * Metriq supports these equations from age 14 onward.
     *   boys  17.686 kcal/kg/day + 658.2
     *   girls 13.384 kcal/kg/day + 692.6
     * Original reference: Schofield WN (1985), Hum Nutr Clin Nutr 39(Suppl 1):5-41.
     * Weight is clamped by [plan] before this is called.
     */
    private fun pediatricBmr(sex: Sex, weightKg: Double): Double {
        val bmr = when (sex) {
            Sex.MALE -> 17.686 * weightKg + 658.2
            Sex.FEMALE -> 13.384 * weightKg + 692.6
        }
        return bmr.coerceAtLeast(0.0)
    }

    fun plan(input: OnboardingInput): PlanResult {
        val warnings = ArrayList<String>()

        // Guardrails: clamp everything, and fall back to sane defaults for non-finite doubles.
        val age = input.age.coerceIn(MIN_AGE, MAX_AGE)
        val heightCm = finiteOr(input.heightCm, DEFAULT_HEIGHT_CM).coerceIn(MIN_HEIGHT_CM, MAX_HEIGHT_CM)
        val weightKg = finiteOr(input.weightKg, DEFAULT_WEIGHT_KG).coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        val bodyFatPercent = input.bodyFatPercent
            ?.let { if (it.isFinite()) it.coerceIn(MIN_BODY_FAT, MAX_BODY_FAT) else null }
        val targetWeightKg = input.targetWeightKg
            ?.let { if (it.isFinite()) it.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG) else null }
        val weeks = input.weeks?.takeIf { it >= 1 }
        if (input.weeks != null && weeks == null) {
            warnings += "Prazo inválido ignorado; informe ao menos 1 semana."
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
            diet = if (isMinor) DietStyle.BALANCED else input.diet,
            proteinTier = ProteinTier.MODERATE,
        )

        val bmr = if (isMinor) pediatricBmr(input.sex, weightKg) else Bmr.forProfile(profile)
        val tdee = bmr * input.activityLevel.factor

        // CONSERVATIVE is the harshest aggressiveness a minor may ever see (10% cut).
        // AGGRESSIVE (CaloriePlan's 0.70 multiplier) is never used for minors.
        val aggressiveness = if (isMinor) Aggressiveness.CONSERVATIVE else Aggressiveness.MODERATE
        var dailyKcal = CaloriePlan.target(tdee, input.goal, aggressiveness).dailyKcal

        val rateCap = if (isMinor) MAX_MINOR_RATE_KG_PER_WEEK else MAX_ADULT_RATE_KG_PER_WEEK
        var plannedWeeks = weeks
        if (targetWeightKg != null && weeks != null) {
            val toLoseKg = weightKg - targetWeightKg
            val goalMismatch = (input.goal == GoalType.CUT && toLoseKg <= 0.0) ||
                (input.goal == GoalType.BULK && toLoseKg >= 0.0)
            if (goalMismatch) {
                warnings += "Peso-alvo incompatível com a meta; objetivo padrão mantido."
            } else {
                val neededRate = abs(toLoseKg) / weeks
                val effectiveWeeks = if (neededRate > rateCap) {
                    ceil(abs(toLoseKg) / rateCap).toInt().coerceAtLeast(1)
                } else {
                    weeks
                }
                if (effectiveWeeks != weeks) {
                    plannedWeeks = effectiveWeeks
                    warnings += "Meta inalcançável no prazo informado: prazo estendido para " +
                        "$effectiveWeeks semanas (limite de ${number(rateCap, 2)} kg/semana)."
                }
                val weeklyRateKg = toLoseKg / effectiveWeeks
                dailyKcal = tdee - weeklyRateKg * Expenditure.KCAL_PER_KG / 7.0
            }
        } else if (targetWeightKg != null || input.weeks != null) {
            warnings += "Informe peso-alvo e prazo juntos; meta padrão mantida."
        }

        if (isMinor) {
            val floor = tdee * (1.0 - MINOR_MAX_DEFICIT_SHARE)
            if (dailyKcal < floor) dailyKcal = floor
            warnings += "Menor de 18 anos: déficit limitado a 10% do gasto diário."
            warnings += "Este cálculo não substitui orientação de um responsável ou profissional de saúde."
            // Low body fat or a cutting intent is where a keto plan would otherwise be implied.
            if (input.goal == GoalType.CUT || (bodyFatPercent != null && bodyFatPercent <= LOW_BODY_FAT_KETO_HINT)) {
                warnings += "Dietas cetogênicas não são indicadas para menores; macros equilibrados foram forçados."
            }
        }

        if (!dailyKcal.isFinite()) dailyKcal = tdee
        dailyKcal = dailyKcal.coerceAtLeast(0.0)

        // Minors are always balanced; adults keep the diet selected in the wizard.
        val diet = profile.diet
        val macros = MacroPlanner.plan(
            kcal = dailyKcal,
            bodyWeightKg = weightKg,
            fatFreeMassKg = profile.fatFreeMassKg,
            goal = input.goal,
            tier = ProteinTier.MODERATE,
            diet = diet,
        )

        val proteinFloorG = MacroPlanner.proteinPerKgFatFreeMass(input.goal, ProteinTier.MODERATE) *
            (profile.fatFreeMassKg ?: 0.75 * weightKg)
        if (dailyKcal < proteinFloorG * 4.0 + FAT_FLOOR_PER_KG * weightKg * 9.0) {
            warnings += "Calorias insuficientes para proteína e gordura mínimas; carboidratos zerados."
        }

        val explanation = buildString {
            append("Com base em ${number(weightKg)} kg, ${number(heightCm, 0)} cm, $age anos ")
            append("e atividade ${activityLabel(input.activityLevel)}, seu gasto estimado é ")
            append("${number(tdee, 0)} kcal/dia. Meta: ${goalLabel(input.goal)} com ")
            append("${number(dailyKcal, 0)} kcal/dia, ${macros.proteinG} g de proteína, ")
            append("${macros.fatG} g de gordura e ${macros.carbG} g de carboidrato.")
            if (isMinor) append(" Cálculo pediátrico (Schofield), com acompanhamento.")
            if (plannedWeeks != null && targetWeightKg != null) {
                append(" Prazo: $plannedWeeks semanas para ${number(targetWeightKg)} kg.")
            }
        }

        return PlanResult(
            bmr = bmr.coerceAtLeast(0.0),
            tdee = tdee,
            dailyKcal = dailyKcal,
            proteinG = macros.proteinG.coerceAtLeast(0),
            fatG = macros.fatG.coerceAtLeast(0),
            carbG = macros.carbG.coerceAtLeast(0),
            isMinor = isMinor,
            warnings = warnings,
            explanation = explanation,
        )
    }

    private fun finiteOr(value: Double, fallback: Double): Double = if (value.isFinite()) value else fallback

    private fun number(value: Double, digits: Int = 1): String {
        val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"))
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = digits
        return format.format(value)
    }

    private fun activityLabel(level: ActivityLevel): String = when (level) {
        ActivityLevel.SEDENTARY -> "sedentária"
        ActivityLevel.LIGHT -> "leve"
        ActivityLevel.MODERATE -> "moderada"
        ActivityLevel.HIGH -> "alta"
        ActivityLevel.ATHLETE -> "de atleta"
    }

    private fun goalLabel(goal: GoalType): String = when (goal) {
        GoalType.CUT -> "perder peso"
        GoalType.MAINTAIN -> "manter o peso"
        GoalType.BULK -> "ganhar massa"
    }
}
