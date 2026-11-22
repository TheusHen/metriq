package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEngineTest {

    private val profile = Profile(
        sex = Sex.MALE,
        age = 30,
        heightCm = 178.0,
        weightKg = 80.0,
        goal = GoalType.MAINTAIN,
        diet = DietStyle.BALANCED,
    )

    // 160*4 + 80*9 + 260*4 = 2400 kcal exactly; fat floor for 80 kg is 40 g.
    private val base = MacroTargets(kcal = 2400, proteinG = 160, fatG = 80, carbG = 260)

    private fun load(intensity: TrainingIntensity) = when (intensity) {
        TrainingIntensity.REST -> TrainingLoad(0.0, 0, 0, intensity)
        TrainingIntensity.LIGHT -> TrainingLoad(200.0, 20, 1, intensity)
        TrainingIntensity.MODERATE -> TrainingLoad(400.0, 50, 1, intensity)
        TrainingIntensity.HARD -> TrainingLoad(600.0, 60, 1, intensity)
    }

    @Test
    fun `intensityOf returns REST without a real training signal`() {
        assertEquals(TrainingIntensity.REST, MacroEngine.intensityOf(500.0, 60, 0, 80.0))
        assertEquals(TrainingIntensity.REST, MacroEngine.intensityOf(4.0, 60, 1, 80.0))
        assertEquals(TrainingIntensity.REST, MacroEngine.intensityOf(500.0, 4, 1, 80.0))
    }

    @Test
    fun `intensityOf classifies by kcal per kg and duration`() {
        assertEquals(TrainingIntensity.LIGHT, MacroEngine.intensityOf(200.0, 20, 1, 80.0))
        assertEquals(TrainingIntensity.MODERATE, MacroEngine.intensityOf(400.0, 50, 1, 80.0))
        assertEquals(TrainingIntensity.HARD, MacroEngine.intensityOf(500.0, 30, 1, 80.0))
        // Low kcal/kg but long duration is not LIGHT, and not MODERATE either.
        assertEquals(TrainingIntensity.HARD, MacroEngine.intensityOf(200.0, 70, 1, 80.0))
    }

    @Test
    fun `intensityOf clamps negative inputs`() {
        assertEquals(TrainingIntensity.REST, MacroEngine.intensityOf(-100.0, -5, -1, -80.0))
        assertEquals(TrainingIntensity.REST, MacroEngine.intensityOf(Double.NaN, 60, 1, 80.0))
    }

    @Test
    fun `weeklyLoad is normalized and empty-safe`() {
        assertEquals(0.0, MacroEngine.weeklyLoad(emptyList()), 0.0001)
        val lightWeek = List(7) { load(TrainingIntensity.LIGHT) }
        assertTrue(MacroEngine.weeklyLoad(lightWeek) in 0.0..1.0)
        assertTrue(MacroEngine.weeklyLoad(lightWeek) < 0.8)
        val hardWeek = List(7) { load(TrainingIntensity.HARD) }
        assertEquals(1.0, MacroEngine.weeklyLoad(hardWeek), 0.0001)
    }

    @Test
    fun `disabled flag keeps base macros`() {
        val result = MacroEngine.adapt(base, profile, load(TrainingIntensity.HARD), emptyList(), false)
        assertEquals(base, result.targets)
        assertEquals(TrainingIntensity.HARD, result.intensity)
        assertEquals(0, result.carbDeltaG)
        assertEquals(0, result.fatDeltaG)
        assertTrue(result.reason.contains("desativada", ignoreCase = true))
    }

    @Test
    fun `null load or REST keeps base macros`() {
        val nullResult = MacroEngine.adapt(base, profile, null, emptyList(), true)
        assertEquals(base, nullResult.targets)
        assertEquals(TrainingIntensity.REST, nullResult.intensity)
        assertTrue(nullResult.reason.contains("Sem sinal de treino"))

        val restResult = MacroEngine.adapt(base, profile, load(TrainingIntensity.REST), emptyList(), true)
        assertEquals(base, restResult.targets)
        assertEquals(0, restResult.carbDeltaG)
    }

    @Test
    fun `light and moderate add small carb amounts taken from fat`() {
        val light = MacroEngine.adapt(base, profile, load(TrainingIntensity.LIGHT), emptyList(), true)
        assertEquals(TrainingIntensity.LIGHT, light.intensity)
        assertTrue(light.carbDeltaG in 0..10)
        assertTrue(light.fatDeltaG <= 0)

        val moderate = MacroEngine.adapt(base, profile, load(TrainingIntensity.MODERATE), emptyList(), true)
        assertEquals(TrainingIntensity.MODERATE, moderate.intensity)
        assertTrue(moderate.carbDeltaG in 1..25)
        assertTrue(moderate.fatDeltaG < 0)
        assertEquals(moderate.fatDeltaG, moderate.targets.fatG - base.fatG)
        assertTrue(moderate.reason.contains("moderado", ignoreCase = true))
    }

    @Test
    fun `hard produces the largest carb bump with a Portuguese reason`() {
        val hard = MacroEngine.adapt(base, profile, load(TrainingIntensity.HARD), emptyList(), true)
        val moderate = MacroEngine.adapt(base, profile, load(TrainingIntensity.MODERATE), emptyList(), true)
        assertTrue(hard.carbDeltaG > moderate.carbDeltaG)
        assertTrue(hard.carbDeltaG in 1..60)
        assertTrue(hard.fatDeltaG < 0)
        assertTrue(hard.reason.contains("pesado", ignoreCase = true))
        assertTrue(hard.reason.contains("carboidrato"))
        assertTrue(hard.reason.contains("gordura"))
    }

    @Test
    fun `keto never increases carbs`() {
        val ketoProfile = profile.copy(diet = DietStyle.KETO)
        val result = MacroEngine.adapt(base, ketoProfile, load(TrainingIntensity.HARD), emptyList(), true)
        assertEquals(base, result.targets)
        assertEquals(0, result.carbDeltaG)
        assertEquals(0, result.fatDeltaG)
        assertEquals(TrainingIntensity.HARD, result.intensity)
        assertTrue(result.reason.contains("cetog", ignoreCase = true))
    }

    @Test
    fun `fat floor is respected when the base is already lean`() {
        val leanBase = MacroTargets(kcal = 2400, proteinG = 160, fatG = 45, carbG = 339)
        val result = MacroEngine.adapt(leanBase, profile, load(TrainingIntensity.HARD), emptyList(), true)
        assertTrue(result.targets.fatG >= 40)
        assertEquals(result.fatDeltaG, result.targets.fatG - leanBase.fatG)
        // Only 5 g of fat above the floor can fund at most ~11 g of carbs.
        assertTrue(result.carbDeltaG <= 11)

        val atFloor = MacroTargets(kcal = 2400, proteinG = 160, fatG = 40, carbG = 340)
        val noRoom = MacroEngine.adapt(atFloor, profile, load(TrainingIntensity.HARD), emptyList(), true)
        assertEquals(atFloor, noRoom.targets)
        assertEquals(0, noRoom.carbDeltaG)
    }

    @Test
    fun `calorie target is preserved within five percent for every intensity`() {
        for (intensity in listOf(TrainingIntensity.LIGHT, TrainingIntensity.MODERATE, TrainingIntensity.HARD)) {
            val result = MacroEngine.adapt(base, profile, load(intensity), emptyList(), true)
            val kcalFromMacros = result.targets.proteinG * 4.0 +
                result.targets.fatG * 9.0 +
                result.targets.carbG * 4.0
            assertTrue(kcalFromMacros >= base.kcal * 0.95)
            assertTrue(kcalFromMacros <= base.kcal * 1.05)
            assertEquals(base.proteinG, result.targets.proteinG)
            assertTrue(result.targets.carbG >= 0)
        }
    }

    @Test
    fun `weekly overreach halves the hard bump`() {
        val hardWeek = List(7) { load(TrainingIntensity.HARD) }
        assertTrue(MacroEngine.weeklyLoad(hardWeek) > 0.8)

        val fresh = MacroEngine.adapt(base, profile, load(TrainingIntensity.HARD), emptyList(), true)
        val fatigued = MacroEngine.adapt(base, profile, load(TrainingIntensity.HARD), hardWeek, true)
        assertTrue(fatigued.carbDeltaG > 0)
        assertTrue(fatigued.carbDeltaG < fresh.carbDeltaG)
        assertEquals(fresh.carbDeltaG / 2, fatigued.carbDeltaG)
        assertTrue(fatigued.reason.contains("reduzido", ignoreCase = true))
    }
}
