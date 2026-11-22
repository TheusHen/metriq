package com.metriq.core

import java.text.NumberFormat
import java.util.Locale
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
    val warnings: List<String>,
    val explanation: String,
)

/**
 * Onboarding goal calculation. Adults go through the same equations used by the app
 * dashboard (Bmr.forProfile -> activity factor -> CaloriePlan.target -> MacroPlanner.plan).
 * Minors use a pediatric resting-energy equation. Every user-entered value is used
 * exactly as provided; unusual values only produce advisory warnings.
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
        val warnings = ArrayList<String>()

        // Age is accepted exactly as entered; only advisory text changes.
        val age = input.age
        if (age < AGE_VALIDATED_MIN) {
            warnings += "Idade abaixo da faixa validada das equações pediátricas; interprete o resultado com cautela."
        } else if (age > AGE_USUAL_MAX) {
            warnings += "Idade fora da faixa usual; confira os dados antes de usar o plano."
        }

        val heightCm = positiveOrFallback(
            input.heightCm, DEFAULT_HEIGHT_CM, "Altura", warnings,
            TYPICAL_HEIGHT_MIN_CM, TYPICAL_HEIGHT_MAX_CM,
        )
        val weightKg = positiveOrFallback(
            input.weightKg, DEFAULT_WEIGHT_KG, "Peso", warnings,
            TYPICAL_WEIGHT_MIN_KG, TYPICAL_WEIGHT_MAX_KG,
        )
        val bodyFatPercent = optionalPositive(
            input.bodyFatPercent, "Gordura corporal", warnings,
            TYPICAL_BODY_FAT_MIN, TYPICAL_BODY_FAT_MAX,
        )
        val targetWeightKg = optionalPositive(
            input.targetWeightKg, "Peso-alvo", warnings,
            TYPICAL_WEIGHT_MIN_KG, TYPICAL_WEIGHT_MAX_KG,
        )
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
            diet = input.diet,
            proteinTier = ProteinTier.MODERATE,
        )

        var bmr = if (isMinor) pediatricBmr(input.sex, weightKg) else Bmr.forProfile(profile)
        if (!bmr.isFinite()) {
            // A physically impossible body-fat percentage can yield a negative fat-free
            // mass and a NaN composition equation; keep the maths finite without changing
            // any user-entered value outside of that sanity path.
            bmr = Bmr.fromBody(weightKg, heightCm, age, input.sex)
            warnings += "Composição corporal inconsistente; gasto basal estimado sem o percentual de gordura."
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
                warnings += "Peso-alvo incompatível com a meta; objetivo padrão mantido."
            } else {
                val neededRate = abs(toLoseKg) / weeks
                val referenceRate = if (isMinor) {
                    REFERENCE_MINOR_RATE_KG_PER_WEEK
                } else {
                    REFERENCE_ADULT_RATE_KG_PER_WEEK
                }
                if (neededRate > referenceRate) {
                    warnings += "Ritmo solicitado ${number(neededRate, 2)} kg/semana acima do referencial de " +
                        "${number(referenceRate, 2)} kg/semana; prazo e meta calculados exatamente como informados."
                }
                val weeklyRateKg = toLoseKg / weeks
                dailyKcal = tdee - weeklyRateKg * Expenditure.KCAL_PER_KG / 7.0
            }
        } else if (targetWeightKg != null || input.weeks != null) {
            warnings += "Informe peso-alvo e prazo juntos; meta padrão mantida."
        }

        if (isMinor) {
            warnings += "Menor de 18 anos: o app sugere deficit de ate 10%, mas voce pode ajustar. " +
                "Acompanhe com um responsavel/profissional."
            warnings += "Este cálculo não substitui orientação de um responsável ou profissional de saúde."
            if (input.diet == DietStyle.KETO) {
                warnings += "Dietas cetogênicas não são indicadas para menores; acompanhe com um responsável/profissional."
            }
        }

        if (!dailyKcal.isFinite()) dailyKcal = tdee
        if (dailyKcal < 0.0) {
            warnings += "A meta resultaria em calorias negativas; valor ajustado para 0 kcal/dia."
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
            bmr = bmr,
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

    private fun positiveOrFallback(
        value: Double,
        fallback: Double,
        field: String,
        warnings: MutableList<String>,
        typicalMin: Double? = null,
        typicalMax: Double? = null,
    ): Double {
        if (!value.isFinite() || value <= 0.0) {
            warnings += "$field inválido; usando ${number(fallback)} como valor neutro."
            return fallback
        }
        warnIfOutsideTypical(value, field, warnings, typicalMin, typicalMax)
        return value
    }

    private fun optionalPositive(
        value: Double?,
        field: String,
        warnings: MutableList<String>,
        typicalMin: Double? = null,
        typicalMax: Double? = null,
    ): Double? {
        if (value == null) return null
        if (!value.isFinite() || value <= 0.0) {
            warnings += "$field inválido; ignorado."
            return null
        }
        warnIfOutsideTypical(value, field, warnings, typicalMin, typicalMax)
        return value
    }

    private fun warnIfOutsideTypical(
        value: Double,
        field: String,
        warnings: MutableList<String>,
        typicalMin: Double?,
        typicalMax: Double?,
    ) {
        if ((typicalMin != null && value < typicalMin) || (typicalMax != null && value > typicalMax)) {
            warnings += "$field fora da faixa usual; valor informado mantido."
        }
    }

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
