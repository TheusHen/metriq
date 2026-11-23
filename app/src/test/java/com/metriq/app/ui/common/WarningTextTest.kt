package com.metriq.app.ui.common

import com.metriq.app.R
import com.metriq.core.DomainWarning
import com.metriq.core.WarningCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Numeric params arrive from core-domain as locale-neutral strings (e.g. "0.5").
 * They must be formatted with the caller's locale, never a hardcoded pt-BR.
 */
class WarningTextTest {

    private fun resolver(id: Int): String = when (id) {
        R.string.warning_goal_cut -> "CUT_LABEL"
        R.string.warning_deadline_detail -> "De %1\$s kg para %2\$s kg em %3\$s semanas."
        R.string.warning_overreaching -> "Sobrecarga."
        R.string.warning_reason_onboarding -> "Plano inicial."
        else -> "R$id"
    }

    @Test
    fun `decimal params use the supplied locale`() {
        val warning = DomainWarning.of(
            WarningCode.RATE_ABOVE_REFERENCE,
            "rateKgPerWeek" to 0.5,
            "referenceKgPerWeek" to 0.25,
        )
        assertEquals(listOf("0,5", "0,25"), resolveWarning(warning, Locale.forLanguageTag("pt-BR"), ::resolver).args)
        assertEquals(listOf("0.5", "0.25"), resolveWarning(warning, Locale.US, ::resolver).args)
    }

    @Test
    fun `integer params use locale grouping and stay integral`() {
        val warning = DomainWarning.of(
            WarningCode.AGE_OUTSIDE_TYPICAL_RANGE,
            "age" to 1234,
            "min" to 10,
            "max" to 100,
        )
        assertEquals(listOf("1.234", "10", "100"), resolveWarning(warning, Locale.forLanguageTag("pt-BR"), ::resolver).args)
        assertEquals(listOf("1,234", "10", "100"), resolveWarning(warning, Locale.US, ::resolver).args)
    }

    @Test
    fun `explicit locale wins over the jvm default`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val warning = DomainWarning.of(WarningCode.RATE_ABOVE_REFERENCE, "rateKgPerWeek" to 0.5)
            assertEquals("0,5", resolveWarning(warning, Locale.forLanguageTag("pt-BR"), ::resolver).args.first())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `enum params are mapped to localized labels`() {
        val warning = DomainWarning.of(WarningCode.TARGET_WEIGHT_GOAL_MISMATCH, "goal" to "CUT")
        assertEquals(listOf("CUT_LABEL"), resolveWarning(warning, Locale.US, ::resolver).args)
    }

    @Test
    fun `plan explanation deadline detail is locale formatted`() {
        val warning = DomainWarning.of(
            WarningCode.PLAN_EXPLANATION,
            "mode" to "DEADLINE", "weightKg" to 80.5, "heightCm" to 175, "age" to 30,
            "activity" to "MODERATE", "tdee" to 2200.0, "goal" to "CUT", "kcal" to 1900,
            "proteinG" to 140, "fatG" to 60, "carbG" to 180, "isMinor" to false,
            "weeks" to 12, "fromKg" to 80.5, "toKg" to 75,
        )
        val args = resolveWarning(warning, Locale.forLanguageTag("pt-BR"), ::resolver).args
        assertEquals("De 80,5 kg para 75 kg em 12 semanas.", args.last())
        assertTrue(args.contains("CUT_LABEL"))
    }

    @Test
    fun `unknown params never crash formatting`() {
        val warning = DomainWarning(WarningCode.NOT_MEDICAL_ADVICE)
        assertTrue(resolveWarning(warning, Locale.US, ::resolver).args.isEmpty())
    }
}
