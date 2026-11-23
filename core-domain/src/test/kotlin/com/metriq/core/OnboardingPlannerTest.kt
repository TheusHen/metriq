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

    private fun hasCode(warnings: List<DomainWarning>, code: WarningCode) =
        warnings.any { it.code == code }

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
        assertEquals("10", result.warnings.first { it.code == WarningCode.MINOR_DEFICIT_ADVISORY }
            .params["maxDeficitPercent"])
        assertTrue(hasCode(result.warnings, WarningCode.NOT_MEDICAL_ADVICE))
    }

    @Test
    fun `relaxed macro floors are reported as a warning in the plan`() {
        val result = OnboardingPlanner.plan(adult(GoalType.CUT, targetWeightKg = 70.0, weeks = 1))
        assertEquals(0.0, result.dailyKcal, 0.0001)
        val total = result.proteinG * 4.0 + result.fatG * 9.0 + result.carbG * 4.0
        assertEquals(0.0, total, 2.0)
        assertTrue(hasCode(result.warnings, WarningCode.MACRO_FLOORS_RELAXED))
    }

    @Test
    fun `minor cut from the requested deadline is not floored to ten percent`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 57.0, weeks = 4))
        val expected = result.tdee - (3.0 / 4.0) * Expenditure.KCAL_PER_KG / 7.0

        assertEquals(expected, result.dailyKcal, 0.001)
        assertTrue(result.dailyKcal < result.tdee * 0.90)
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ADVISORY))
        assertFalse(hasCode(result.warnings, WarningCode.KCAL_BELOW_FLOOR))
    }

    @Test
    fun `minor extreme requested rate is honored with advisory warnings only`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 40.0, weeks = 1))

        assertEquals(0.0, result.dailyKcal, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ADVISORY))
        assertTrue(hasCode(result.warnings, WarningCode.NEGATIVE_KCAL_CLAMPED))
    }

    @Test
    fun `minor rate above reference keeps the requested weeks and warns`() {
        val result = OnboardingPlanner.plan(minor(GoalType.CUT, targetWeightKg = 55.0, weeks = 2))

        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertEquals("0.25", result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
            .params["referenceKgPerWeek"])
        assertEquals("2", result.explanation.params["weeks"])
    }

    @Test
    fun `adult rate above reference is honored with the exact requested weeks`() {
        val input = adult(GoalType.CUT).copy(weightKg = 90.0, targetWeightKg = 88.0, weeks = 2)
        val result = OnboardingPlanner.plan(input)

        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertEquals("0.5", result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
            .params["referenceKgPerWeek"])
        assertEquals(result.tdee - 1.0 * Expenditure.KCAL_PER_KG / 7.0, result.dailyKcal, 0.001)
        assertEquals("2", result.explanation.params["weeks"])
    }

    @Test
    fun `rate warning is advisory and keeps an unreachable deadline`() {
        val result = OnboardingPlanner.plan(adult(GoalType.CUT).copy(weightKg = 95.0, targetWeightKg = 80.0, weeks = 3))

        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertEquals("3", result.explanation.params["weeks"])
    }

    @Test
    fun `out of typical range inputs are used as entered with warnings`() {
        val result = OnboardingPlanner.plan(
            OnboardingInput(Sex.MALE, 34, 250.0, 350.0, 70.0, ActivityLevel.MODERATE, GoalType.MAINTAIN),
        )
        val profile = Profile(Sex.MALE, 34, 250.0, 350.0, 70.0, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.HEIGHT_OUTSIDE_TYPICAL_RANGE))
        assertTrue(hasCode(result.warnings, WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE))
        assertTrue(hasCode(result.warnings, WarningCode.BODY_FAT_OUTSIDE_TYPICAL_RANGE))
        assertTrue(result.warnings.none { it.code == WarningCode.HEIGHT_INVALID_FALLBACK })
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
        assertEquals("175", fallback.warnings.first { it.code == WarningCode.HEIGHT_INVALID_FALLBACK }
            .params["fallback"])
        assertEquals("75", fallback.warnings.first { it.code == WarningCode.WEIGHT_INVALID_FALLBACK }
            .params["fallback"])
        assertTrue(hasCode(fallback.warnings, WarningCode.BODY_FAT_INVALID))
    }

    @Test
    fun `minor keto diet is honored with an advisory, not forced balanced`() {
        val result = OnboardingPlanner.plan(minor(GoalType.MAINTAIN).copy(diet = DietStyle.KETO))

        assertTrue(hasCode(result.warnings, WarningCode.KETO_NOT_RECOMMENDED_FOR_MINORS))
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ADVISORY))
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
        assertTrue(hasCode(result.warnings, WarningCode.INSUFFICIENT_KCAL_FOR_FLOORS))
    }

    @Test
    fun `explanation is a structured code with plain numeric params`() {
        val result = OnboardingPlanner.plan(adult())

        assertEquals(WarningCode.PLAN_EXPLANATION, result.explanation.code)
        assertEquals("ONBOARDING", result.explanation.params["mode"])
        assertEquals("78", result.explanation.params["weightKg"])
        assertEquals("178", result.explanation.params["heightCm"])
        assertEquals("34", result.explanation.params["age"])
        assertEquals("MODERATE", result.explanation.params["activity"])
        assertEquals("MAINTAIN", result.explanation.params["goal"])
        assertTrue(result.explanation.params["tdee"]!!.toDouble() > 0.0)
        assertEquals(result.proteinG.toString(), result.explanation.params["proteinG"])
        assertEquals(result.fatG.toString(), result.explanation.params["fatG"])
        assertEquals(result.carbG.toString(), result.explanation.params["carbG"])
        assertEquals("false", result.explanation.params["isMinor"])
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
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ADVISORY))
    }

    @Test
    fun `age below fourteen is accepted with pediatric math and an age warning`() {
        val input = OnboardingInput(Sex.FEMALE, 8, 160.0, 50.0)
        val result = OnboardingPlanner.plan(input)

        assertTrue(result.isMinor)
        assertEquals(13.384 * 50.0 + 692.6, result.bmr, 0.001)
        assertEquals("8", result.warnings.first { it.code == WarningCode.AGE_BELOW_PEDIATRIC_VALIDATION }
            .params["age"])
        assertFalse(hasCode(result.warnings, WarningCode.AGE_OUTSIDE_TYPICAL_RANGE))
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
        assertTrue(hasCode(result.warnings, WarningCode.MINOR_DEFICIT_ADVISORY))
        assertFalse(hasCode(result.warnings, WarningCode.AGE_BELOW_PEDIATRIC_VALIDATION))
        assertEquals("12", result.explanation.params["age"])
    }

    @Test
    fun `age one hundred twenty is accepted with adult math and an age warning`() {
        val input = adult().copy(age = 120)
        val result = OnboardingPlanner.plan(input)
        val profile = Profile(Sex.MALE, 120, 178.0, 78.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertFalse(result.isMinor)
        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.bmr * ActivityLevel.MODERATE.factor, result.tdee, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.AGE_OUTSIDE_TYPICAL_RANGE))
    }

    @Test
    fun `weight twenty kilos is used as entered with a warning`() {
        val result = OnboardingPlanner.plan(adult().copy(weightKg = 20.0))
        val profile = Profile(Sex.MALE, 34, 178.0, 20.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.tdee, result.dailyKcal, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE))
    }

    @Test
    fun `weight three hundred fifty kilos is used as entered with a warning`() {
        val result = OnboardingPlanner.plan(adult().copy(weightKg = 350.0))
        val profile = Profile(Sex.MALE, 34, 178.0, 350.0, null, ActivityLevel.MODERATE, GoalType.MAINTAIN)

        assertEquals(Bmr.forProfile(profile), result.bmr, 0.001)
        assertEquals(result.tdee, result.dailyKcal, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.WEIGHT_OUTSIDE_TYPICAL_RANGE))
    }

    @Test
    fun `target weight outside the typical range is used with a warning`() {
        val result = OnboardingPlanner.plan(
            adult(GoalType.CUT).copy(weightKg = 78.0, targetWeightKg = 20.0, weeks = 4),
        )

        assertTrue(hasCode(result.warnings, WarningCode.TARGET_WEIGHT_OUTSIDE_TYPICAL_RANGE))
        assertEquals("20", result.explanation.params["targetKg"])
        assertEquals("4", result.explanation.params["weeks"])
        assertEquals(0.0, result.dailyKcal, 0.001)
        assertTrue(hasCode(result.warnings, WarningCode.NEGATIVE_KCAL_CLAMPED))
    }

    @Test
    fun `two week target above one kilo per week is honored with a rate warning`() {
        val result = OnboardingPlanner.plan(
            adult(GoalType.CUT).copy(weightKg = 90.0, targetWeightKg = 86.0, weeks = 2),
        )

        assertTrue(hasCode(result.warnings, WarningCode.RATE_ABOVE_REFERENCE))
        assertEquals("0.5", result.warnings.first { it.code == WarningCode.RATE_ABOVE_REFERENCE }
            .params["referenceKgPerWeek"])
        assertEquals(result.tdee - 2.0 * Expenditure.KCAL_PER_KG / 7.0, result.dailyKcal, 0.001)
        assertEquals("2", result.explanation.params["weeks"])
    }
}
