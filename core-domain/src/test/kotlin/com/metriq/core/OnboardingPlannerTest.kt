package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlannerTest {

    private fun adult(
        goal: GoalType = GoalType.MAINTAIN,
        bodyFatPercent: Double? = null,
        diet: DietStyle = DietStyle.BALANCED,
        targetWeightKg: Double? = null,
        weeks: Int? = null,
    ) = OnboardingInput(
        sex = Sex.MALE,
        age = 34,
        heightCm = 178.0,
        weightKg = 78.0,
        bodyFatPercent = bodyFatPercent,
        activityLevel = ActivityLevel.MODERATE,
        goal = goal,
        diet = diet,
        targetWeightKg = targetWeightKg,
        weeks = weeks,
    )

    private fun minor(
        goal: GoalType = GoalType.MAINTAIN,
        weightKg: Double = 60.0,
        bodyFatPercent: Double? = null,
        targetWeightKg: Double? = null,
        weeks: Int? = null,
    ) = OnboardingInput(
        sex = Sex.MALE,
        age = 15,
        heightCm = 165.0,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        activityLevel = ActivityLevel.MODERATE,
        goal = goal,
        targetWeightKg = targetWeightKg,
        weeks = weeks,
    )

    @Test
    fun `adult maintain uses bmr and activity factor with no warnings`() {
        val result = OnboardingPlanner.plan(adult())
        val profile = Profile(Sex.MALE, 34, 178.0, 78.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertFalse(result.isMinor)
        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertEquals(result.tdee, result.dailyKcal, 0.001)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `adult cut uses the moderate deficit and bulk adds five percent`() {
        val cut = OnboardingPlanner.plan(adult(GoalType.CUT))
        val bulk = OnboardingPlanner.plan(adult(GoalType.BULK))
        val maintain = OnboardingPlanner.plan(adult())

        assertEquals(maintain.tdee * 0.85, cut.dailyKcal, 0.001)
        assertEquals(maintain.tdee * 1.05, bulk.dailyKcal, 0.001)
        assertEquals(maintain.bmr, cut.bmr, 0.001)
    }

    @Test
    fun `minor uses the schofield pediatric bmr instead of the adult equation`() {
        val result = OnboardingPlanner.plan(minor())
        val expectedBmr = 17.686 * 60.0 + 658.2

        assertTrue(result.isMinor)
        assertEquals(expectedBmr, result.bmr, 0.001)
        assertFalse(result.bmr == Bmr.forProfile(Profile(Sex.MALE, 15, 165.0, 60.0)))
        assertEquals(expectedBmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(result.warnings.any { it.contains("deficit de ate 10%") })
        assertTrue(result.warnings.contains("Este cálculo não substitui orientação de um responsável ou profissional de saúde."))
    }

    @Test
    fun `minor cut from the requested deadline is not floored to ten percent`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 57.0, weeks = 4))
        val expected = result.tdee - (3.0 / 4.0) * Expenditure.KCAL_PER_KG / 7.0

        assertEquals(expected, result.dailyKcal, 0.001)
        assertTrue(result.dailyKcal < result.tdee * 0.90)
        assertTrue(result.warnings.any { it.contains("deficit de ate 10%") })
        assertFalse(result.warnings.any { it.contains("déficit limitado") })
    }

    @Test
    fun `minor extreme requested rate is honored with advisory warnings only`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 40.0, weeks = 1))

        assertEquals(0.0, result.dailyKcal, 0.001)
        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("deficit de ate 10%") })
        assertTrue(result.warnings.any { it.contains("calorias negativas") })
    }

    @Test
    fun `minor rate above reference keeps the requested weeks and warns`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 55.0, weeks = 2))

        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,25") })
        assertFalse(result.warnings.any { it.contains("estendido") })
        assertTrue(result.explanation.contains("2 semanas"))
    }

    @Test
    fun `adult rate above reference is honored with the exact requested weeks`() {
        val input = adult(GoalType.CUT).copy(weightKg = 90.0, targetWeightKg = 88.0, weeks = 2)
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,5") })
        assertFalse(result.warnings.any { it.contains("estendido") })
        assertEquals(result.tdee - 1.0 * Expenditure.KCAL_PER_KG / 7.0, result.dailyKcal, 0.001)
        assertTrue(result.explanation.contains("2 semanas"))
    }

    @Test
    fun `rate warning is advisory and keeps an unreachable deadline`() {
        val result = OnboardingPlanner.plan(adult(GoalType.CUT).copy(weightKg = 95.0, targetWeightKg = 80.0, weeks = 3))

        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertFalse(result.warnings.any { it.contains("estendido") })
        assertTrue(result.explanation.contains("3 semanas"))
    }

    @Test
    fun `out of typical range inputs are used as entered with warnings`() {
        val result = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, 34, 250.0, 350.0, 70.0, ActivityLevel.MODERATE, GoalType.MAINTAIN),
        )
        val profile = Profile(Sex.MALE, 34, 250.0, 350.0, 70.0, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(result.warnings.any { it.contains("Altura fora da faixa usual") })
        assertTrue(result.warnings.any { it.contains("Peso fora da faixa usual") })
        assertTrue(result.warnings.any { it.contains("Gordura corporal fora da faixa usual") })
        assertTrue(result.warnings.none { it.contains("Altura inválido") })
    }

    @Test
    fun `non finite doubles fall back to safe defaults with named warnings`() {
        val fallback = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, 30, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN),
        )
        val expected = OnboardingPlanner.plan(OnboardingInput(Sex.MALE, 30, 175.0, 75.0, null))

        assertEquals(expected.bmr, fallback.bmr, 0.001)
        assertEquals(expected.tdee, fallback.tdee, 0.001)
        assertEquals(expected.proteinG, fallback.proteinG)
        assertTrue(fallback.tdee.isFinite())
        assertTrue(fallback.dailyKcal.isFinite())
        assertTrue(fallback.warnings.any { it.startsWith("Altura inválido") })
        assertTrue(fallback.warnings.any { it.startsWith("Peso inválido") })
        assertTrue(fallback.warnings.any { it.startsWith("Gordura corporal inválido") })
    }

    @Test
    fun `minor keto diet is honored with an advisory, not forced balanced`() {
        val result = OnboardingPlanner.plan(minor(GoalType.MAINTAIN).copy(diet = DietStyle.KETO))

        assertTrue(result.warnings.any { it.contains("cetogênica") })
        assertTrue(result.warnings.any { it.contains("menores") })
        assertTrue(result.carbG <= MacroPlanner.KETO_MAX_CARB_G.toInt() + 1)
    }

    @Test
    fun `macros are consistent with the calorie target`() {
        val result = OnboardingPlanner.plan(adult())
        val macroKcal = result.proteinG * 4.0 + result.fatG * 9.0 + result.carbG * 4.0

        assertEquals(result.dailyKcal, macroKcal, 12.0)
        assertTrue(result.carbG > 0)
    }

    @Test
    fun `no negative values are produced when calories cannot cover the floors`() {
        val input = OnboardingInput(
            sex = Sex.FEMALE,
            age = 100,
            heightCm = 100.0,
            weightKg = 30.0,
            bodyFatPercent = 60.0,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = GoalType.CUT,
            targetWeightKg = 25.0,
            weeks = 1,
        )
        val result = OnboardingPlanner.plan(input)

        assertEquals(0.0, result.dailyKcal, 0.001)
        assertEquals(0, result.carbG)
        assertTrue(result.proteinG >= 0)
        assertTrue(result.fatG >= 0)
        assertTrue(result.bmr >= 0.0)
        assertTrue(result.tdee >= 0.0)
        assertTrue(result.warnings.any { it.contains("carboidratos zerados") })
    }

    @Test
    fun `explanation is a short portuguese paragraph with the key figures`() {
        val result = OnboardingPlanner.plan(adult())

        assertTrue(result.explanation.contains("78 kg"))
        assertTrue(result.explanation.contains("178 cm"))
        assertTrue(result.explanation.contains("34 anos"))
        assertTrue(result.explanation.contains("gasto estimado"))
        assertTrue(result.explanation.contains("proteína"))
        assertTrue(result.explanation.length < 400)
    }

    @Test
    fun `adult diet choice changes the macro split`() {
        val balanced = OnboardingPlanner.plan(adult(diet = DietStyle.BALANCED))
        val keto = OnboardingPlanner.plan(adult(diet = DietStyle.KETO))
        assertTrue(keto.carbG < balanced.carbG)
        assertTrue(keto.fatG > balanced.fatG)
        assertEquals(balanced.dailyKcal, keto.dailyKcal, 1.0)
    }

    @Test
    fun `age fourteen uses the pediatric equation with advisory language only`() {
        val input = OnboardingInput(
            sex = Sex.MALE, age = 14, heightCm = 165.0, weightKg = 60.0,
            activityLevel = ActivityLevel.MODERATE, goal = GoalType.CUT,
        )
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.isMinor)
        assertEquals(17.686 * 60.0 + 658.2, result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertEquals(result.tdee * 0.90, result.dailyKcal, 0.001)
        assertTrue(result.warnings.any { it.contains("Menor de 18") })
    }

    @Test
    fun `age below fourteen is accepted with pediatric math and an age warning`() {
        val input = OnboardingInput(Sex.FEMALE, 8, 160.0, 50.0)
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.isMinor)
        assertEquals(13.384 * 50.0 + 692.6, result.bmr, 0.001)
        assertTrue(result.warnings.any { it.contains("Idade abaixo da faixa validada") })
    }

    @Test
    fun `age seventeen is the last pediatric year and eighteen switches to adult`() {
        val base = adult().copy(age = 17, weightKg = 60.0, heightCm = 165.0)
        val seventeen = OnboardingPlanner.plan(base)
        val eighteen = OnboardingPlanner.plan(base.copy(age = 18))

        assertTrue(seventeen.isMinor)
        assertEquals(17.686 * 60.0 + 658.2, seventeen.bmr, 0.001)
        assertFalse(eighteen.isMinor)
        assertEquals(
            Bmr.forProfile(Profile(Sex.MALE, 18, 165.0, 60.0, null, ActivityLevel.MODERATE)),
            eighteen.bmr,
            0.001,
        )
        assertTrue(eighteen.warnings.isEmpty())
    }

    @Test
    fun `age twelve is accepted with pediatric math and an age warning`() {
        val input = adult().copy(age = 12, weightKg = 60.0, heightCm = 165.0)
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.isMinor)
        assertEquals(17.686 * 60.0 + 658.2, result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(result.warnings.any { it.contains("Menor de 18") })
        assertTrue(result.warnings.none { it.contains("faixa validada") })
        assertTrue(result.explanation.contains("12 anos"))
    }

    @Test
    fun `age one hundred twenty is accepted with adult math and an age warning`() {
        val input = adult().copy(age = 120)
        val result = OnboardingPlanner.plan(input)
        val profile = Profile(Sex.MALE, 120, 178.0, 78.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertFalse(result.isMinor)
        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(result.warnings.any { it.contains("Idade fora da faixa usual") })
    }

    @Test
    fun `weight twenty kilos is used as entered with a warning`() {
        val result = OnboardingPlanner.plan(adult().copy(weightKg = 20.0))
        val profile = Profile(Sex.MALE, 34, 178.0, 20.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.tdee, result.dailyKcal, 0.001)
        assertTrue(result.warnings.any { it.contains("Peso fora da faixa usual") })
    }

    @Test
    fun `weight three hundred fifty kilos is used as entered with a warning`() {
        val result = OnboardingPlanner.plan(adult().copy(weightKg = 350.0))
        val profile = Profile(Sex.MALE, 34, 178.0, 350.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.tdee, result.dailyKcal, 0.001)
        assertTrue(result.warnings.any { it.contains("Peso fora da faixa usual") })
    }

    @Test
    fun `target weight outside the typical range is used with a warning`() {
        val result = OnboardingPlanner.plan(
            adult(GoalType.CUT).copy(weightKg = 78.0, targetWeightKg = 20.0, weeks = 4),
        )

        assertTrue(result.warnings.any { it.contains("Peso-alvo fora da faixa usual") })
        assertTrue(result.explanation.contains("20 kg"))
        assertTrue(result.explanation.contains("4 semanas"))
        assertEquals(0.0, result.dailyKcal, 0.001)
        assertTrue(result.warnings.any { it.contains("calorias negativas") })
    }

    @Test
    fun `two week target above one kilo per week is honored with a rate warning`() {
        val result = OnboardingPlanner.plan(
            adult(GoalType.CUT).copy(weightKg = 90.0, targetWeightKg = 86.0, weeks = 2),
        )

        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,5") })
        assertFalse(result.warnings.any { it.contains("estendido") })
        assertEquals(result.tdee - 2.0 * Expenditure.KCAL_PER_KG / 7.0, result.dailyKcal, 0.001)
        assertTrue(result.explanation.contains("2 semanas"))
    }
}
