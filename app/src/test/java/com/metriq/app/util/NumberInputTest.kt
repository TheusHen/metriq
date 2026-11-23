package com.metriq.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression tests for locale-independent parsing of user-entered numbers. */
class NumberInputTest {

    @Test
    fun `double parses comma decimal separator`() {
        assertEquals(150.5, parseDoubleLoose("150,5")!!, 0.0)
    }

    @Test
    fun `double parses dot decimal separator and trims whitespace`() {
        assertEquals(150.5, parseDoubleLoose(" 150.5 ")!!, 0.0)
    }

    @Test
    fun `double rejects junk, blank and non finite input`() {
        assertNull(parseDoubleLoose("not a number"))
        assertNull(parseDoubleLoose("   "))
        assertNull(parseDoubleLoose(null))
        assertNull(parseDoubleLoose("NaN"))
    }

    @Test
    fun `int parses comma and dot decimal separator`() {
        assertEquals(30, parseIntLoose("30"))
        assertEquals(30, parseIntLoose("30,0"))
        assertEquals(7, parseIntLoose(" 7.0 "))
    }

    @Test
    fun `int rejects junk and fractional input`() {
        assertNull(parseIntLoose("abc"))
        assertNull(parseIntLoose("30,5"))
        assertNull(parseIntLoose(null))
    }
}
