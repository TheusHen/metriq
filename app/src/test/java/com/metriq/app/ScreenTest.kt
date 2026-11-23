package com.metriq.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** System back must unwind the same parent chain as the in-screen back buttons. */
class ScreenTest {

    @Test
    fun `dashboard is the root`() {
        assertNull(Screen.DASHBOARD.parent)
    }

    @Test
    fun `log tools return to search`() {
        listOf(
            Screen.SEARCH,
            Screen.CREATE,
            Screen.SCAN,
            Screen.LABEL_SCAN,
            Screen.FOOD_PHOTO,
            Screen.VOICE_LOG,
        ).forEach { assertEquals(Screen.DASHBOARD, it.parent) }
    }

    @Test
    fun `secondary screens return to dashboard`() {
        listOf(
            Screen.WEIGHT,
            Screen.PROFILE,
            Screen.STEPS,
            Screen.HABITS,
            Screen.BODY,
            Screen.RECIPES,
            Screen.MICRONUTRIENTS,
            Screen.ANALYTICS,
        ).forEach { assertEquals(Screen.DASHBOARD, it.parent) }
    }

    @Test
    fun `settings and its children unwind one level at a time`() {
        assertEquals(Screen.PROFILE, Screen.SETTINGS.parent)
        assertEquals(Screen.SETTINGS, Screen.AI_MODELS.parent)
        assertEquals(Screen.SETTINGS, Screen.INTEGRATIONS.parent)
    }
}
