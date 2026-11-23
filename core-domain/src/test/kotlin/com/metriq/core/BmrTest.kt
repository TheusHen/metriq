package com.metriq.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BmrTest {

    @Test
    fun `body equation is the published Mifflin St Jeor formula`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, Bmr.fromBody(80.0, 180.0, 30, Sex.MALE), 0.0001)
        // 10*80 + 6.25*180 - 5*30 - 161 = 1614
        assertEquals(1614.0, Bmr.fromBody(80.0, 180.0, 30, Sex.FEMALE), 0.0001)
    }

    @Test
    fun `female bmr is 166 kcal lower than male at identical inputs`() {
        val male = Bmr.fromBody(80.0, 180.0, 30, Sex.MALE)
        val female = Bmr.fromBody(80.0, 180.0, 30, Sex.FEMALE)
        assertEquals(166.0, male - female, 0.0001)
    }

    @Test
    fun `age has no discontinuity at sixty`() {
        val at59 = Bmr.fromBody(80.0, 180.0, 59, Sex.MALE)
        val at60 = Bmr.fromBody(80.0, 180.0, 60, Sex.MALE)
        val at61 = Bmr.fromBody(80.0, 180.0, 61, Sex.MALE)
        assertEquals(1635.0, at59, 0.0001)
        assertEquals(1630.0, at60, 0.0001)
        assertEquals(1625.0, at61, 0.0001)
        assertEquals(5.0, at59 - at60, 0.0001)
        assertEquals(5.0, at60 - at61, 0.0001)
    }

    @Test
    fun `composition equation is Katch McArdle and has no age discontinuity`() {
        // 370 + 21.6 * 60 = 1666, independent of fat mass and age.
        assertEquals(1666.0, Bmr.fromComposition(60.0, 15.0, 30), 0.0001)
        assertEquals(1666.0, Bmr.fromComposition(60.0, 15.0, 59), 0.0001)
        assertEquals(1666.0, Bmr.fromComposition(60.0, 15.0, 60), 0.0001)
        assertEquals(1666.0, Bmr.fromComposition(60.0, 15.0, 61), 0.0001)
    }

    @Test
    fun `forProfile accepts an explicit historical peak`() {
        val profile = Profile(Sex.MALE, 30, 180.0, 80.0)
        val base = Bmr.fromBody(80.0, 180.0, 30, Sex.MALE)
        assertEquals(base, Bmr.forProfile(profile), 0.0001)
        assertEquals(base, Bmr.forProfile(profile, peakKg = null), 0.0001)
        // 80 kg is < 90% of 90 kg, so the below-peak correction applies.
        assertEquals(base * 0.97, Bmr.forProfile(profile, peakKg = 90.0), 0.0001)
        assertEquals(base, Bmr.forProfile(profile, peakKg = 85.0), 0.0001)
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
