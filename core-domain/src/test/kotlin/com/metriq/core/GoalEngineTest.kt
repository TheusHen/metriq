package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun hasCode(warnings: List<DomainWarning>, code: WarningCode) =
        warnings.any { it.code == code }

    @Test
    fun `same kcal returns the suggested plan unchanged`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt()), profile(input), false,
        )

        assertFalse(result.didClamp)
        assertEquals(result.suggestedKcal, result.appliedKcal)
        assertEquals(suggested, result.plan)
        assertEquals(WarningCode.SUMMARY_GOAL_UNCHANGED, result.summary.code)
        assertEquals(result.suggestedKcal.toString(), result.summary.params["kcal"])
        assertTrue(result.warnings == suggested.warnings)
    }

    @Test
    fun `same kcal still carries newly generated warnings into the plan`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input).copy(warnings = emptyList())
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(suggested.dailyKcal.roundToInt(), reason = "  férias  "),
            profile(input),
            false,
        )

        assertEquals(suggested.dailyKcal.roundToInt(), result.appliedKcal)
        val reason = DomainWarning(WarningCode.GOAL_REASON, mapOf("reason" to "férias"))
        assertTrue(result.warnings.contains(reason))
        assertTrue(result.plan.warnings.contains(reason))
        assertEquals(suggested.copy(warnings = result.warnings), result.plan)
    }

    @Test
    fun `same kcal for a minor still carries the safety advisory`() {
        val input = minor()
        // The dashboard builds its suggested plan with no warnings, so the
        // safety code must come from GoalEngine itself (and only once).
        val suggested = OnboardingPlanner.plan(input).copy(warnings = emptyList())
        val result = GoalEngine.recalculate(
            suggested, GoalOverride(suggested.dailyKcal.roundToInt()), profile(input), true,
        )

        assertFalse(result.didClamp)
        assertEquals(1, result.warnings.count { it.code == WarningCode.NOT_MEDICAL_ADVICE })
    }

    @Test
    fun `adult goal below the floor is honored with an advisory warning`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(100), profile(input), false)

        assertFalse(result.didClamp)
        assertEquals(100, result.appliedKcal)
        assertEquals(100.0, result.plan.dailyKcal, 0.0001)
        assertTrue(hasCode(result.warnings, WarningCode.KCAL_BELOW_FLOOR))
        val floorWarning = result.warnings.first { it.code == WarningCode.KCAL_BELOW_FLOOR }
        assertTrue(floorWarning.params["floorKcal"]!!.toInt() > 100)
        assertEquals("100", floorWarning.params["appliedKcal"])
        assertEquals(WarningCode.SUMMARY_GOAL_ADJUSTED, result.summary.code)
        assertEquals("100", result.summary.params["toKcal"])
    }

    @Test
    fun `minor goal below ten percent of tdee is honored with an advisory`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_000), profile(input), true)

        assertFalse(result.didClamp)
        assertEquals(1_000, result.appliedKcal)
        assertEquals(1_000.0, result.plan.dailyKcal, 0.0001)
        val warning = result.warnings.first { it.code == WarningCode.MINOR_DEFICIT_ABOVE_LIMIT }
        assertEquals("10", warning.params["limitPercent"])
        assertTrue(hasCode(result.warnings, WarningCode.NOT_MEDICAL_ADVICE))
    }

    @Test
    fun `floors are relaxed with a warning when calories cannot fit them`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(100), profile(input), false)

        val total = result.plan.proteinG * 4.0 + result.plan.fatG * 9.0 + result.plan.carbG * 4.0
        assertEquals(100.0, total, 2.0)
        assertTrue(result.plan.proteinG >= 0 && result.plan.fatG >= 0 && result.plan.carbG >= 0)
        assertTrue(hasCode(result.warnings, WarningCode.MACRO_FLOORS_RELAXED))
        assertTrue(hasCode(result.plan.warnings, WarningCode.MACRO_FLOORS_RELAXED))
    }

    @Test
    fun `target weight and deadline drive the applied kcal in the editor`() {
        val input = adult(weightKg = 78.0)
        val suggested = OnboardingPlanner.plan(input)
        // Mifflin-St Jeor 1727.5 * 1.55 = 2677.625; 1.0 kg/week costs 7716/7 = 1102.29.
        assertEquals(2677.625, suggested.tdee, 0.001)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(2246, targetWeightKg = 70.0, weeks = 8),
            profile(input),
            false,
        )

        assertEquals(1575, result.appliedKcal)
        assertEquals(1575.0, result.plan.dailyKcal, 0.001)
        assertFalse(result.didClamp)
        val impliedRate = (suggested.tdee - result.plan.dailyKcal) * 7.0 / Expenditure.KCAL_PER_KG
        assertEquals(1.0, impliedRate, 0.001)
    }

    @Test
    fun `manual kcal with no deadline remains pinned`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_900), profile(input), false)

        assertEquals(1_900, result.appliedKcal)
        assertEquals(1_900.0, result.plan.dailyKcal, 0.0001)
        assertFalse(result.didClamp)
    }

    @Test
    fun `follow expenditure recomputes the goal from current expenditure`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val current = suggested.copy(tdee = suggested.tdee * 1.1)
        val result = GoalEngine.recalculate(
            current,
            GoalOverride(999, followExpenditure = true),
            profile(input),
            false,
        )

        assertEquals(2_945, result.appliedKcal)
        assertEquals(current.tdee, result.plan.dailyKcal, 1.0)
        assertFalse(result.didClamp)
    }

    @Test
    fun `unreachable deadline is clamped to zero and reported`() {
        val input = adult(weightKg = 90.0)
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(2_000, targetWeightKg = 80.0, weeks = 2),
            profile(input),
            false,
        )

        assertEquals(0, result.appliedKcal)
        assertTrue(result.didClamp)
        val rate = result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
        assertEquals("0.5", rate.params["referenceKgPerWeek"])
    }

    @Test
    fun `minor weekly rate above reference is clamped and warned about`() {
        val input = minor()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(1_000, targetWeightKg = 50.0, weeks = 2),
            profile(input),
            true,
        )

        assertTrue(result.didClamp)
        assertEquals(0, result.appliedKcal)
        val rate = result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
        assertEquals("0.25", rate.params["referenceKgPerWeek"])
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
        assertTrue(hasCode(result.warnings, WarningCode.TARGET_AND_WEEKS_REQUIRED))
        assertTrue(hasCode(result.warnings, WarningCode.NEGATIVE_KCAL_CLAMPED))
        assertTrue(hasCode(result.warnings, WarningCode.GOAL_REASON))
        assertEquals("teste", result.warnings.first { it.code == WarningCode.GOAL_REASON }.params["reason"])
        assertEquals("0", result.summary.params["toKcal"])
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
        assertTrue(hasCode(result.warnings, WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS))
        assertTrue(result.plan.carbG <= MacroPlanner.KETO_MAX_CARB_G.toInt() + 1)
    }

    @Test
    fun `summary is a structured code with plain numeric params`() {
        val input = adult()
        val suggested = OnboardingPlanner.plan(input)
        val result = GoalEngine.recalculate(suggested, GoalOverride(1_500), profile(input), false)

        assertEquals(WarningCode.SUMMARY_GOAL_ADJUSTED, result.summary.code)
        assertEquals(suggested.dailyKcal.roundToInt().toString(), result.summary.params["fromKcal"])
        assertEquals("1500", result.summary.params["toKcal"])
        assertTrue(result.summary.params["proteinChange"] in listOf("MAINTAINED", "REDUCED"))
        assertTrue(result.summary.params["carbChange"] in listOf("REDUCED", "INCREASED", "MAINTAINED"))
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
        assertEquals("INCREASED", result.summary.params["carbChange"])
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
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ABOVE_LIMIT))
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
        assertTrue(hasCode(result.warnings, WarningCode.KCAL_BELOW_FLOOR))
        assertEquals("900", result.summary.params["toKcal"])
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
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ABOVE_LIMIT))
    }

    @Test
    fun `two week target above one kilo per week computes the required kcal`() {
        val input = adult(weightKg = 90.0)
        val suggested = OnboardingPlanner.plan(input)
        // 2863.625 - 2.0 * 7716 / 7 = 659.05 kcal/day.
        val result = GoalEngine.recalculate(
            suggested,
            GoalOverride(suggested.dailyKcal.roundToInt(), targetWeightKg = 86.0, weeks = 2),
            profile(input),
            false,
        )

        assertEquals(659, result.appliedKcal)
        assertEquals(659.0, result.plan.dailyKcal, 0.001)
        assertFalse(result.didClamp)
        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertEquals("0.5", result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
            .params["referenceKgPerWeek"])
    }
}
