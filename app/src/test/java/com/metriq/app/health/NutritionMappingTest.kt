package com.metriq.app.health

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure logic only: no Health Connect, WorkManager or Android framework calls.
 * These rules pin the two things that can silently corrupt a re-export:
 * which meal a log hour belongs to, and the stable client record id.
 */
class NutritionMappingTest {

    @Test
    fun `hour maps to the expected meal slot`() {
        assertEquals(MealSlot.BREAKFAST, mealSlotForHour(7))
        assertEquals(MealSlot.BREAKFAST, mealSlotForHour(10))
        assertEquals(MealSlot.LUNCH, mealSlotForHour(11))
        assertEquals(MealSlot.LUNCH, mealSlotForHour(15))
        assertEquals(MealSlot.DINNER, mealSlotForHour(16))
        assertEquals(MealSlot.DINNER, mealSlotForHour(20))
        assertEquals(MealSlot.SNACK, mealSlotForHour(21))
        assertEquals(MealSlot.SNACK, mealSlotForHour(3))
    }

    @Test
    fun `out of range hours fall back to snack`() {
        assertEquals(MealSlot.SNACK, mealSlotForHour(-1))
        assertEquals(MealSlot.SNACK, mealSlotForHour(99))
    }

    @Test
    fun `client record id is stable and prefixed`() {
        assertEquals("metriq-log-42", MetriqNutritionRecord.clientRecordId(42))
        assertEquals("metriq-log-0", MetriqNutritionRecord.clientRecordId(0))
        assertEquals("metriq-log-9876543210", MetriqNutritionRecord.clientRecordId(9876543210L))
    }
}
