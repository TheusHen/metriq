package com.metriq.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the malformed weigh-in date that crashed dashboard construction. */
class DateInputTest {

    @Test
    fun `accepts zero padded ISO date`() {
        assertTrue(DateInput.isValidIsoDate("2026-01-05"))
    }

    @Test
    fun `rejects non zero padded ISO date`() {
        assertFalse(DateInput.isValidIsoDate("2026-1-5"))
    }

    @Test
    fun `rejects impossible calendar date`() {
        assertFalse(DateInput.isValidIsoDate("2026-02-30"))
    }

    @Test
    fun `rejects blank and junk input`() {
        assertFalse(DateInput.isValidIsoDate(""))
        assertFalse(DateInput.isValidIsoDate("   "))
        assertFalse(DateInput.isValidIsoDate("not a date"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertTrue(DateInput.isValidIsoDate("  2026-01-05  "))
    }
}
