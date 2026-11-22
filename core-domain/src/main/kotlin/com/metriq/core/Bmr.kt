package com.metriq.core

/**
 * Basal metabolic rate. The three published Metriq-compatible equations, plus the
 * deficit / below-peak corrections. All results are kcal/day.
 */
object Bmr {

    /** Weight + height form. */
    fun fromBody(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val ageCoef = if (age > 60) 4.9 else 1.96
        val sexCoef = if (sex == Sex.MALE) 0.0 else 213.8
        return 129.6 * Math.pow(weightKg, 0.55) +
            0.011 * heightCm * heightCm -
            ageCoef * age -
            sexCoef
    }

    /** Fat-free mass + fat mass form. */
    fun fromComposition(ffmKg: Double, fatMassKg: Double, age: Int): Double {
        val ageCoef = if (age > 60) 2.75 else 1.1
        val ffm = Math.pow(ffmKg, 0.7)
        return 50.2 * ffm + 40.5 * (ffm * Math.pow(fatMassKg, 0.066)) - ageCoef * age
    }

    /** Athlete form (>= 7 h/week of intense exercise). */
    fun athlete(ffmKg: Double): Double = 40.4 * Math.pow(ffmKg, 0.932)

    /**
     * Corrections applied after the base equation: 5% down in a deficit, 3% down when the
     * current weight is more than 10% below the recorded peak, 8% when both hold.
     */
    fun corrected(bmr: Double, inDeficit: Boolean, belowPeak: Boolean): Double = when {
        inDeficit && belowPeak -> bmr * 0.92
        inDeficit -> bmr * 0.95
        belowPeak -> bmr * 0.97
        else -> bmr
    }

    fun isBelowPeak(currentKg: Double, peakKg: Double): Boolean = currentKg < peakKg * 0.9

    /** Best available estimate for a profile. */
    fun forProfile(profile: Profile, peakKg: Double = profile.weightKg, inDeficit: Boolean = false): Double {
        val ffm = profile.fatFreeMassKg
        val fm = profile.fatMassKg
        val base = if (ffm != null && fm != null) fromComposition(ffm, fm, profile.age)
        else fromBody(profile.weightKg, profile.heightCm, profile.age, profile.sex)
        return corrected(base, inDeficit, isBelowPeak(profile.weightKg, peakKg))
    }
}
