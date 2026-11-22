package com.metriq.app

import com.metriq.core.GoalOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
