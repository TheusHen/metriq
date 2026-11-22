package com.metriq.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BmrTest {

    @Test
    fun `female bmr is lower than male at identical inputs`() {
        val male = Bmr.fromBody(80.0, 180.0, 30, Sex.MALE)
        val female = Bmr.fromBody(80.0, 180.0, 30, Sex.FEMALE)
        assertEquals(213.8, male - female, 0.001)
    }

    @Test
    fun `age past sixty applies the larger age coefficient`() {
        val young = Bmr.fromBody(80.0, 180.0, 30, Sex.MALE)
        val older = Bmr.fromBody(80.0, 180.0, 70, Sex.MALE)
        val expectedDrop = 4.9 * 70 - 1.96 * 30
        assertEquals(expectedDrop, young - older, 0.001)
    }

    @Test
    fun `corrections multiply as published`() {
        val base = 2000.0
        assertEquals(1900.0, Bmr.corrected(base, inDeficit = true, belowPeak = false), 0.001)
        assertEquals(1940.0, Bmr.corrected(base, inDeficit = false, belowPeak = true), 0.001)
        assertEquals(1840.0, Bmr.corrected(base, inDeficit = true, belowPeak = true), 0.001)
        assertEquals(base, Bmr.corrected(base, inDeficit = false, belowPeak = false), 0.001)
    }

    @Test
    fun `composition equation rises with fat free mass`() {
        val lean = Bmr.fromComposition(60.0, 15.0, 30)
        val heavier = Bmr.fromComposition(70.0, 15.0, 30)
        assertTrue(heavier > lean)
    }

    @Test
    fun `profile without body fat uses the weight and height equation`() {
        val profile = Profile(Sex.MALE, 30, 180.0, 80.0)
        assertEquals(Bmr.fromBody(80.0, 180.0, 30, Sex.MALE), Bmr.forProfile(profile), 0.001)
    }

    @Test
    fun `below peak threshold is ten percent`() {
        assertTrue(Bmr.isBelowPeak(80.0, 90.0))
        assertTrue(!Bmr.isBelowPeak(81.5, 90.0))
    }
}
