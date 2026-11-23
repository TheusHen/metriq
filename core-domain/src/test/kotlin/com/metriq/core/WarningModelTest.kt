package com.metriq.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WarningModelTest {

    @Test
    fun `warning params keep plain locale independent numeric values`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val warning = DomainWarning.of(
                WarningCode.RATE_ABOVE_REFERENCE,
                "rateKgPerWeek" to 1.25,
                "referenceKgPerWeek" to 0.5,
            )
            assertEquals(WarningCode.RATE_ABOVE_REFERENCE, warning.code)
            assertEquals("1.25", warning.params["rateKgPerWeek"])
            assertEquals("0.5", warning.params["referenceKgPerWeek"])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `empty params are the default`() {
        assertEquals(emptyMap<String, String>(), DomainWarning(WarningCode.NOT_MEDICAL_ADVICE).params)
    }

    @Test
    fun `kotlin enums keep descriptive upper snake names`() {
        assertEquals("KCAL_BELOW_FLOOR", WarningCode.KCAL_BELOW_FLOOR.name)
        assertEquals("PLAN_EXPLANATION", WarningCode.PLAN_EXPLANATION.name)
    }
}
