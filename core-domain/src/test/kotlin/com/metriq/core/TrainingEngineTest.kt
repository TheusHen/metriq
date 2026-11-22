package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingEngineTest {

    private val profile = Profile(
        sex = Sex.MALE,
        age = 30,
        heightCm = 178.0,
        weightKg = 80.0,
        goal = GoalType.MAINTAIN,
        diet = DietStyle.BALANCED,
    )

    // 160*4 + 80*9 + 260*4 = 2400 kcal; fat floor for 80 kg is 40 g.
    private val base = MacroTargets(kcal = 2400, proteinG = 160, fatG = 80, carbG = 260)

    private fun signal(
        intensity: TrainingIntensity,
        kcal: Double = 600.0,
        minutes: Int = 60,
        volume: Double = 12_000.0,
        muscles: Set<String> = setOf("Peito", "Costas", "Pernas"),
    ) = TrainingSignal(kcal, 8_000L, 1, volume, minutes, muscles, intensity)

    @Test
    fun `health connect only signal has no volume or muscles`() {
        val result = TrainingEngine.fromHealthConnect(0.0, 12_000L, 0, 0, 80.0)

        assertEquals(TrainingIntensity.REST, result.intensity)
        assertEquals(0.0, result.volumeKg, 0.0001)
        assertTrue(result.muscles.isEmpty())
        assertEquals(12_000L, result.steps!!)
        assertEquals(0.0, result.activeKcal, 0.0001)
    }

    @Test
    fun `health connect intensity follows kcal per kg and duration`() {
        val light = TrainingEngine.fromHealthConnect(200.0, 5_000L, 1, 20, 80.0)
        val moderate = TrainingEngine.fromHealthConnect(400.0, 7_000L, 1, 50, 80.0)
        val hard = TrainingEngine.fromHealthConnect(500.0, 9_000L, 1, 30, 80.0)

        assertEquals(TrainingIntensity.LIGHT, light.intensity)
        assertEquals(TrainingIntensity.MODERATE, moderate.intensity)
        assertEquals(TrainingIntensity.HARD, hard.intensity)
    }

    @Test
    fun `health connect clamps negative and non finite inputs`() {
        val result = TrainingEngine.fromHealthConnect(Double.NaN, -5L, -3, -10, Double.POSITIVE_INFINITY)

        assertEquals(0.0, result.activeKcal, 0.0001)
        assertEquals(0.0, result.durationMinutes.toDouble(), 0.0001)
        assertEquals(0, result.sessions)
        assertEquals(0L, result.steps!!)
        assertEquals(TrainingIntensity.REST, result.intensity)
    }

    @Test
    fun `hevy derives hard intensity from volume per kg and duration`() {
        val result = TrainingEngine.fromHevy(
            activeKcal = 300.0,
            steps = 6_000L,
            volumeKg = 12_000.0, // 150 kg/kg bodyweight
            durationMinutes = 70,
            muscles = setOf("Peito", "Costas", "Pernas", "Ombros"),
            sessions = 1,
            bodyWeightKg = 80.0,
        )

        assertEquals(TrainingIntensity.HARD, result.intensity)
        assertEquals(12_000.0, result.volumeKg, 0.0001)
        assertEquals(70, result.durationMinutes)
        assertEquals(4, result.muscles.size)
    }

    @Test
    fun `hevy derives light and moderate tiers from volume`() {
        val light = TrainingEngine.fromHevy(100.0, null, 800.0, 20, emptySet(), 1, 80.0)
        val moderate = TrainingEngine.fromHevy(200.0, null, 4_000.0, 40, setOf("Pernas"), 1, 80.0)

        assertEquals(TrainingIntensity.LIGHT, light.intensity)
        assertEquals(TrainingIntensity.MODERATE, moderate.intensity)
        assertNull(light.steps)
    }

    @Test
    fun `hevy energy only promotes one tier as a supporting signal`() {
        val result = TrainingEngine.fromHevy(600.0, null, 0.0, 20, emptySet(), 1, 80.0)

        // kcal/kg = 7.5 would be HARD on energy alone, but volume + duration are light.
        assertEquals(TrainingIntensity.MODERATE, result.intensity)
    }

    @Test
    fun `hevy normalizes muscle names and clamps bad numbers`() {
        val result = TrainingEngine.fromHevy(
            activeKcal = Double.NEGATIVE_INFINITY,
            steps = -2L,
            volumeKg = Double.NaN,
            durationMinutes = -30,
            muscles = setOf(" Peito ", "", "Costas"),
            sessions = -1,
            bodyWeightKg = Double.NaN,
        )

        assertEquals(0.0, result.activeKcal, 0.0001)
        assertEquals(0.0, result.volumeKg, 0.0001)
        assertEquals(0, result.durationMinutes)
        assertEquals(0, result.sessions)
        assertEquals(0L, result.steps!!)
        assertEquals(2, result.muscles.size)
        assertTrue(result.muscles.contains("Peito"))
        assertTrue(result.muscles.none { it.isEmpty() })
        assertEquals(TrainingIntensity.REST, result.intensity)
    }

    @Test
    fun `weekly load averages the last seven signals and clamps to the unit range`() {
        val rest = signal(TrainingIntensity.REST, kcal = 0.0, minutes = 0, volume = 0.0)
        val hard = signal(TrainingIntensity.HARD)

        assertEquals(0.0, TrainingEngine.weeklyLoad(emptyList()), 0.0001)
        assertEquals(1.0, TrainingEngine.weeklyLoad(List(7) { hard }), 0.0001)
        // An eighth, older signal must be outside the rolling window.
        assertEquals(1.0, TrainingEngine.weeklyLoad(listOf(rest) + List(7) { hard }), 0.0001)
        val mixed = TrainingEngine.weeklyLoad(listOf(rest) + List(6) { hard })
        assertTrue(mixed in 0.0..1.0)
        assertTrue(mixed < 1.0)
        assertEquals(0.0, TrainingEngine.weeklyLoad(List(7) { rest }), 0.0001)
    }

    @Test
    fun `weekly load stays finite for malformed signals`() {
        val malformed = TrainingSignal(
            activeKcal = Double.NaN,
            steps = null,
            sessions = -1,
            volumeKg = Double.NEGATIVE_INFINITY,
            durationMinutes = -5,
            muscles = emptySet(),
            intensity = TrainingIntensity.HARD,
        )

        val load = TrainingEngine.weeklyLoad(listOf(malformed))
        assertTrue(load.isFinite())
        assertTrue(load in 0.0..1.0)
    }

    @Test
    fun `adaptWithSignal disabled null and REST keep the base with a signal reason`() {
        val disabled = MacroEngine.adaptWithSignal(base, profile, signal(TrainingIntensity.HARD), emptyList(), false)
        assertEquals(base, disabled.targets)
        assertEquals(0, disabled.carbDeltaG)
        assertTrue(disabled.reason.contains("sinal de treino", ignoreCase = true))

        val missing = MacroEngine.adaptWithSignal(base, profile, null, emptyList(), true)
        assertEquals(base, missing.targets)
        assertEquals(TrainingIntensity.REST, missing.intensity)
        assertTrue(missing.reason.contains("sinal de treino", ignoreCase = true))

        val rest = MacroEngine.adaptWithSignal(
            base, profile, signal(TrainingIntensity.REST, kcal = 0.0, minutes = 0, volume = 0.0), emptyList(), true,
        )
        assertEquals(base, rest.targets)
        assertEquals(0, rest.carbDeltaG)
        assertTrue(rest.reason.contains("sinal de treino", ignoreCase = true))
    }

    @Test
    fun `adaptWithSignal keeps keto unchanged`() {
        val keto = profile.copy(diet = DietStyle.KETO)
        val result = MacroEngine.adaptWithSignal(base, keto, signal(TrainingIntensity.HARD), emptyList(), true)

        assertEquals(base, result.targets)
        assertEquals(0, result.carbDeltaG)
        assertEquals(0, result.fatDeltaG)
        assertTrue(result.reason.contains("cetog", ignoreCase = true))
    }

    @Test
    fun `adaptWithSignal gives hard a bigger carb bump than moderate and stays isoenergetic`() {
        val hard = MacroEngine.adaptWithSignal(base, profile, signal(TrainingIntensity.HARD), emptyList(), true)
        val moderate = MacroEngine.adaptWithSignal(
            base, profile,
            signal(TrainingIntensity.MODERATE, kcal = 300.0, minutes = 40, volume = 4_000.0),
            emptyList(), true,
        )

        assertTrue(hard.carbDeltaG > moderate.carbDeltaG)
        assertTrue(hard.carbDeltaG > 0)
        assertTrue(hard.fatDeltaG < 0)
        assertTrue(hard.reason.contains("pesado", ignoreCase = true))
        assertTrue(hard.reason.contains("carboidrato"))
        assertTrue(hard.reason.contains("gordura"))
        assertEquals(base.proteinG, hard.targets.proteinG)
        assertTrue(hard.targets.fatG >= 40)
        val macroKcal = hard.targets.proteinG * 4.0 + hard.targets.fatG * 9.0 + hard.targets.carbG * 4.0
        assertTrue(macroKcal in base.kcal * 0.95..base.kcal * 1.05)
    }

    @Test
    fun `adaptWithSignal halves the hard bump when weekly load is over 0_8`() {
        val fatiguedWeek = List(7) { signal(TrainingIntensity.HARD) }
        assertTrue(TrainingEngine.weeklyLoad(fatiguedWeek) > 0.8)

        val fresh = MacroEngine.adaptWithSignal(base, profile, signal(TrainingIntensity.HARD), emptyList(), true)
        val fatigued = MacroEngine.adaptWithSignal(base, profile, signal(TrainingIntensity.HARD), fatiguedWeek, true)

        assertTrue(fatigued.carbDeltaG > 0)
        assertEquals(fresh.carbDeltaG / 2, fatigued.carbDeltaG)
        assertTrue(fatigued.reason.contains("reduzido", ignoreCase = true))
    }

    @Test
    fun `adaptWithSignal respects the fat floor when there is no room`() {
        val atFloor = base.copy(fatG = 40, carbG = 340)
        val result = MacroEngine.adaptWithSignal(atFloor, profile, signal(TrainingIntensity.HARD), emptyList(), true)

        assertEquals(atFloor, result.targets)
        assertEquals(0, result.carbDeltaG)
        assertTrue(result.targets.fatG >= 40)
        assertFalse(result.reason.contains("+0 g"))
    }
}
