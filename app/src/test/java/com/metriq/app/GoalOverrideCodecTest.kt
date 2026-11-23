package com.metriq.app

import com.metriq.core.GoalOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalOverrideCodecTest {

    @Test
    fun `decode returns null for blank or malformed json`() {
        assertNull(GoalOverrideCodec.decode(""))
        assertNull(GoalOverrideCodec.decode("{not json"))
    }

    @Test
    fun `decode rejects a non-positive calorie goal`() {
        assertNull(GoalOverrideCodec.decode("""{"dailyKcal":0}"""))
    }

    @Test
    fun `encode and decode preserve all fields`() {
        val goal = GoalOverride(
            dailyKcal = 2100,
            targetWeightKg = 72.5,
            weeks = 12,
            reason = "teste",
        )

        assertEquals(goal, GoalOverrideCodec.decode(GoalOverrideCodec.encode(goal)))
    }

    @Test
    fun `decode tolerates missing optional fields`() {
        val goal = GoalOverrideCodec.decode("""{"dailyKcal":1900}""")

        assertEquals(GoalOverride(1900, null, null, null), goal)
    }

    @Test
    fun `decode defaults followExpenditure to false for legacy json`() {
        val legacy = """{"dailyKcal":1900,"targetWeightKg":72.5,"weeks":12,"reason":"antigo"}"""

        val goal = GoalOverrideCodec.decode(legacy)

        assertEquals(false, goal?.followExpenditure)
        assertEquals(72.5, goal?.targetWeightKg ?: 0.0, 0.0)
        assertEquals(12, goal?.weeks)
        assertEquals("antigo", goal?.reason)
    }

    @Test
    fun `encode and decode preserve followExpenditure`() {
        val goal = GoalOverride(
            dailyKcal = 2100,
            targetWeightKg = 72.5,
            weeks = 12,
            reason = "teste",
            followExpenditure = true,
        )

        assertEquals(goal, GoalOverrideCodec.decode(GoalOverrideCodec.encode(goal)))
        assertTrue(GoalOverrideCodec.encode(goal).contains("\"followExpenditure\":true"))
    }

    @Test
    fun `decode reads an explicit false followExpenditure`() {
        val goal = GoalOverrideCodec.decode("""{"dailyKcal":1900,"followExpenditure":false}""")

        assertEquals(false, goal?.followExpenditure)
    }
}
