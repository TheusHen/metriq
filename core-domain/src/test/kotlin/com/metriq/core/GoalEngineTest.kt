package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun `adult goal below the floor is honored with an advisory warning`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(100), profile(input), false)

        assertFalse(result.didClamp)
        assertEquals(100, result.appliedKcal)
        assertEquals(100.0, result.plan.dailyKcal, 0.0001)
        assertTrue(result.warnings.any { it.contains("piso estimado") })
        assertTrue(result.warnings.any { it.contains("profissional") })
        assertTrue(result.summary.contains("para 100"))
    }

    @Test
    fun `minor goal below ten percent of tdee is honored with an advisory`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_000), profile(input), true)

        assertFalse(result.didClamp)
        assertEquals(1_000, result.appliedKcal)
        assertEquals(1_000.0, result.plan.dailyKcal, 0.0001)
        assertTrue(result.warnings.any { it.contains("Deficit acima de 10%") })
        assertTrue(result.warnings.any { it.contains("profissional de saúde") })
    }

    @Test
    fun `protein and fat floors are respected in the resulting macros`() {
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
    fun `adult weekly rate above reference is a warning only`() {
        val input = adult(weightKg = 90.0)
        val suggested = OnboardingPlanner.plan(input)
        val requested = suggested.dailyKcal.roundToInt() - 100
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(requested, targetWeightKg = 80.0, weeks = 2),
            profile(input),
            false,
        )

        assertFalse(result.didClamp)
        assertEquals(requested, result.appliedKcal)
        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,5") })
        assertFalse(result.warnings.any { it.contains("estendido") })
    }

    @Test
    fun `minor weekly rate above reference is a warning only`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(1_000, targetWeightKg = 50.0, weeks = 2),
            profile(input),
            true,
        )

        assertFalse(result.didClamp)
        assertEquals(1_000, result.appliedKcal)
        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,25") })
        assertFalse(result.warnings.any { it.contains("estendido") })
    }

    @Test
    fun `invalid override and target inputs stay finite without NaN`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(-50, targetWeightKg = Double.NaN, weeks = 0, reason = "  teste  "),
            profile(input),
            false,
        )

        assertFalse(result.didClamp)
        assertEquals(0, result.appliedKcal)
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
    fun `minor keto profile is honored with an advisory, not forced balanced`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val ketoProfile = profile(input).copy(diet = DietStyle.KETO)
        val requested = suggested.dailyKcal.roundToInt() - 50
        val result = GoalEngine.recalculate(suggested, GoalOverride(requested), ketoProfile, true)

        assertFalse(result.didClamp)
        assertEquals(requested, result.appliedKcal)
        assertTrue(result.warnings.any { it.contains("cetogênica") })
        assertTrue(result.plan.carbG <= MacroPlanner.KETO_MAX_CARB_G.toInt() + 1)
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
    fun `age fourteen gets the same minor advisory without a cap`() {
        val input = minor().copy(age = 14)
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(900), profile(input), true,
        )

        assertFalse(result.didClamp)
        assertEquals(900, result.appliedKcal)
        assertTrue(result.warnings.any { it.contains("Deficit acima de 10%") })
        assertTrue(result.plan.isMinor)
    }

    @Test
    fun `goal override nine hundred kcal is honored exactly with advisory and no clamp`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(900), profile(input), false)

        assertFalse(result.didClamp)
        assertEquals(900, result.appliedKcal)
        assertEquals(900.0, result.plan.dailyKcal, 0.0001)
        assertTrue(result.warnings.any { it.contains("piso estimado") })
        assertTrue(result.summary.contains("para 900"))
    }

    @Test
    fun `minor request at thirty percent of tdee is honored with an advisory`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val requested = (suggested.tdee * 0.70).roundToInt()
        val result = GoalEngine.recalculate(suggested, GoalOverride(requested), profile(input), true)

        assertFalse(result.didClamp)
        assertEquals(requested, result.appliedKcal)
        assertEquals(requested.toDouble(), result.plan.dailyKcal, 0.0001)
        assertTrue(result.warnings.any { it.contains("Deficit acima de 10%") })
    }

    @Test
    fun `two week target above one kilo per week is honored with a rate warning`() {
        val input = adult(weightKg = 90.0)
        val suggested = OnboardingPlanner.plan(input)
        val requested = suggested.dailyKcal.roundToInt()
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(requested, targetWeightKg = 86.0, weeks = 2),
            profile(input),
            false,
        )

        assertFalse(result.didClamp)
        assertEquals(requested, result.appliedKcal)
        assertTrue(result.warnings.any { it.startsWith("Ritmo solicitado") })
        assertTrue(result.warnings.any { it.contains("0,5") })
        assertFalse(result.warnings.any { it.contains("estendido") })
    }
}
