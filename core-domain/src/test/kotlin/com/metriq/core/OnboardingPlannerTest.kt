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
        assertTrue(result.warnings.contains("Menor de 18 anos: déficit limitado a 10% do gasto diário."))
        assertTrue(result.warnings.contains("Este cálculo não substitui orientação de um responsável ou profissional de saúde."))
    }

    @Test
    fun `minor caps any deficit at ten percent of tdee`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 50.0, weeks = 4))

        assertEquals(result.tdee * 0.90, result.dailyKcal, 0.001)
        assertTrue(result.dailyKcal >= result.tdee * 0.90 - 0.001)
        assertTrue(result.warnings.any { it.contains("déficit limitado a 10%") })
    }

    @Test
    fun `minor aggressive intent is ignored even with an extreme requested rate`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 40.0, weeks = 1))
        val aggressive = CaloriePlan.target(result.tdee, GoalType.CUT, Aggressiveness.AGGRESSIVE).dailyKcal

        assertTrue(result.dailyKcal > aggressive)
        assertEquals(result.tdee * 0.90, result.dailyKcal, 0.001)
    }

    @Test
    fun `minor rate cap extends the timeline and warns`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 55.0, weeks = 2))

        assertTrue(result.warnings.any { it.contains("estendido para 20 semanas") })
        assertTrue(result.warnings.any { it.contains("0,25") })
        assertTrue(result.dailyKcal >= result.tdee * 0.90 - 0.001)
    }

    @Test
    fun `adult rate cap extends the timeline and uses the half kilo weekly delta`() {
        val input = adult(GoalType.CUT).copy(weightKg = 90.0, targetWeightKg = 80.0, weeks = 2)
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.warnings.any { it.contains("estendido para 20 semanas") })
        assertTrue(result.warnings.any { it.contains("0,5") })
        assertEquals(result.tdee - 0.5 * Expenditure.KCAL_PER_KG / 7.0, result.dailyKcal, 0.001)
    }

    @Test
    fun `unreachable target warning is emitted when the deadline is too short`() {
        val result = OnboardingPlanner.plan(adult(GoalType.CUT).copy(weightKg = 95.0, targetWeightKg = 80.0, weeks = 3))

        assertTrue(result.warnings.any { it.startsWith("Meta inalcançável no prazo informado") })
        assertTrue(result.explanation.contains("semanas para"))
    }

    @Test
    fun `out of range inputs are clamped`() {
        val clamped = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, -5, 50.0, 999.0, 99.0, ActivityLevel.MODERATE, GoalType.MAINTAIN),
        )
        val expected = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, 10, 100.0, 300.0, 60.0, ActivityLevel.MODERATE, GoalType.MAINTAIN),
        )

        assertEquals(expected.bmr, clamped.bmr, 0.001)
        assertEquals(expected.tdee, clamped.tdee, 0.001)
        assertEquals(expected.dailyKcal, clamped.dailyKcal, 0.001)
        assertEquals(expected.proteinG, clamped.proteinG)
        assertTrue(clamped.isMinor)
    }

    @Test
    fun `non finite doubles fall back to safe defaults`() {
        val fallback = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, 30, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN),
        )
        val expected = OnboardingPlanner.plan(OnboardingInput(Sex.MALE, 30, 175.0, 75.0, null))

        assertEquals(expected.bmr, fallback.bmr, 0.001)
        assertEquals(expected.tdee, fallback.tdee, 0.001)
        assertEquals(expected.proteinG, fallback.proteinG)
        assertTrue(fallback.tdee.isFinite())
        assertTrue(fallback.dailyKcal.isFinite())
    }

    @Test
    fun `keto is forced to balanced macros for a minor`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, bodyFatPercent = 8.0))

        assertTrue(result.warnings.any { it.contains("cetogênica") })
        assertTrue(result.warnings.any { it.contains("equilibrados") })
        assertTrue(result.carbG > MacroPlanner.KETO_MAX_CARB_G.toInt())
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
}
