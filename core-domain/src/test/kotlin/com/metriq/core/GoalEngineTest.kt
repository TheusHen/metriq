package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.roundToInt

class GoalEngineTest {

    private fun adult(
        weightKg: Double = 78.0,
        goal: GoalType = GoalType.MAINTAIN,
        diet: DietStyle = DietStyle.BALANCED,
    ): OnboardingInput = OnboardingInput(
        sex = Sex.MALE,
        age = 34,
        heightCm = 178.0,
        weightKg = weightKg,
        activityLevel = ActivityLevel.MODERATE,
        goal = goal,
        diet = diet,
    )

    private fun minor(weightKg: Double = 60.0): OnboardingInput = OnboardingInput(
        sex = Sex.MALE,
        age = 15,
        heightCm = 165.0,
        weightKg = weightKg,
        activityLevel = ActivityLevel.MODERATE,
    )

    private fun profile(input: OnboardingInput) = Profile(
        sex = input.sex,
        age = input.age,
        heightCm = input.heightCm,
        weightKg = input.weightKg,
        bodyFatPercent = input.bodyFatPercent,
        activityLevel = input.activityLevel,
        goal = input.goal,
        diet = input.diet,
    )

    @Test
    fun `same kcal returns the suggested plan unchanged`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt()), profile(input), false,
        )

        assertFalse(result.didClamp)
        assertEquals(result.suggestedKcal, result.appliedKcal)
        assertSame(suggested, result.plan)
        assertTrue(result.summary.contains("mantida em"))
        assertTrue(result.warnings == suggested.warnings)
    }

    @Test
    fun `same kcal for a minor still carries the safety advisory`() {
        val input = minor()
        // The dashboard builds its suggested plan with no warnings, so the
        // safety text must come from GoalEngine itself (and only once).
        val suggested = OnboardingPlanner.plan(input).copy(warnings = emptyList())
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt()), profile(input), true,
        )

        assertFalse(result.didClamp)
        assertEquals(1, result.warnings.count { it.contains("profissional de saúde") })
    }

    @Test
    fun `adult goal below the floor is clamped and warned`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(100), profile(input), false)

        assertTrue(result.didClamp)
        assertTrue(result.appliedKcal > 100)
        assertTrue(result.appliedKcal >= ceil(suggested.bmr * 1.10).toInt())
        assertTrue(result.warnings.any { it.contains("mínimo seguro", ignoreCase = true) })
        assertEquals(result.appliedKcal.toDouble(), result.plan.dailyKcal, 0.0001)
    }

    @Test
    fun `minor goal can never cut more than ten percent of tdee`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_000), profile(input), true)

        assertTrue(result.didClamp)
        assertEquals(ceil(suggested.tdee * 0.90).toInt(), result.appliedKcal)
        assertTrue(result.warnings.any { it.contains("déficit limitado a 10%") })
        assertTrue(result.warnings.any { it.contains("profissional de saúde") })
    }

    @Test
    fun `protein and fat floors survive recalculation`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(100), profile(input), false)

        val expectedProtein = MacroPlanner.proteinPerKgFatFreeMass(GoalType.MAINTAIN, ProteinTier.MODERATE) *
            0.75 * input.weightKg
        assertEquals(expectedProtein, result.plan.proteinG.toDouble(), 1.0)
        assertTrue(result.plan.fatG >= 0.5 * input.weightKg)
        assertEquals(suggested.proteinG, result.plan.proteinG)
    }

    @Test
    fun `adult weekly rate cap extends the timeline with a Portuguese warning`() {
        val input = adult(weightKg = 90.0)
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(suggested.dailyKcal.roundToInt() - 100, targetWeightKg = 80.0, weeks = 2),
            profile(input),
            false,
        )

        assertFalse(result.didClamp)
        assertTrue(result.warnings.any { it.contains("estendido para 20 semanas") })
        assertTrue(result.warnings.any { it.contains("0,5") })
    }

    @Test
    fun `minor weekly rate cap is a quarter kilo and extends the timeline`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(1_000, targetWeightKg = 50.0, weeks = 2),
            profile(input),
            true,
        )

        assertTrue(result.warnings.any { it.contains("estendido para 40 semanas") })
        assertTrue(result.warnings.any { it.contains("0,25") })
        assertEquals(ceil(suggested.tdee * 0.90).toInt(), result.appliedKcal)
    }

    @Test
    fun `invalid override and target inputs are clamped without NaN`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(-50, targetWeightKg = Double.NaN, weeks = 0, reason = "  teste  "),
            profile(input),
            false,
        )

        assertTrue(result.appliedKcal >= 0)
        assertTrue(result.plan.dailyKcal.isFinite())
        assertTrue(result.plan.proteinG >= 0 && result.plan.fatG >= 0 && result.plan.carbG >= 0)
        assertTrue(result.warnings.any { it.contains("peso-alvo e prazo juntos") })
        assertTrue(result.warnings.any { it.contains("teste") })
        assertTrue(result.summary.isNotBlank())
    }

    @Test
    fun `macros stay consistent with the applied calories for keto adults`() {
        val input = adult(diet = DietStyle.KETO, goal = GoalType.CUT)
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt() + 150), profile(input), false,
        )

        val macroKcal = result.plan.proteinG * 4.0 + result.plan.fatG * 9.0 + result.plan.carbG * 4.0
        assertEquals(result.appliedKcal.toDouble(), macroKcal, 12.0)
        assertTrue(result.plan.carbG <= MacroPlanner.KETO_MAX_CARB_G.toInt() + 1)
        assertTrue(result.plan.fatG > suggested.fatG)
    }

    @Test
    fun `minors always get a balanced split even with a keto profile`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val ketoProfile = profile(input).copy(diet = DietStyle.KETO)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt() - 50), ketoProfile, true,
        )

        assertTrue(result.plan.carbG > MacroPlanner.KETO_MAX_CARB_G.toInt())
        assertTrue(result.appliedKcal >= ceil(suggested.tdee * 0.90).toInt() - 1)
    }

    @Test
    fun `summary is a short pt BR sentence with formatted numbers`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_500), profile(input), false)

        assertTrue(result.summary.contains("Meta ajustada de"))
        assertTrue(result.summary.contains("para"))
        assertTrue(result.summary.contains("kcal/dia"))
        assertTrue(result.summary.contains("—"))
        assertTrue(result.summary.contains("proteína"))
        assertTrue(result.summary.contains("carboidrato"))
        assertTrue(result.summary.length < 160)
    }

    @Test
    fun `increasing the goal increases carbs and keeps protein`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt() + 300), profile(input), false,
        )

        assertFalse(result.didClamp)
        assertEquals(result.suggestedKcal + 300, result.appliedKcal)
        assertTrue(result.plan.carbG > suggested.carbG)
        assertEquals(suggested.proteinG, result.plan.proteinG)
        assertTrue(result.summary.contains("carboidrato aumentado"))
    }

    @Test
    fun `existing warnings are carried into the recalculated plan`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(1_000), profile(input), true,
        )

        assertTrue(result.plan.warnings.containsAll(suggested.warnings))
        assertTrue(result.warnings.containsAll(suggested.warnings))
    }

    @Test
    fun `age fourteen gets the same minor deficit cap as older minors`() {
        val input = minor().copy(age = 14)
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(900), profile(input), true,
        )

        assertTrue(result.didClamp)
        assertEquals(ceil(suggested.tdee * 0.90).toInt(), result.appliedKcal)
        assertTrue(result.warnings.any { it.contains("10%") })
        assertTrue(result.plan.isMinor)
    }
}
