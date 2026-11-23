package com.metriq.core

/**
 * Basal metabolic rate. Two published equations, plus the deficit / below-peak
 * corrections. All results are kcal/day.
 */
object Bmr {

    /**
     * Mifflin-St Jeor (1990), the validated weight/height/age/sex equation.
     * `10 * kg + 6.25 * cm - 5 * age + (5 male / -161 female)`.
     * Age is linear throughout life: there is no discontinuity at 60.
     */
    fun fromBody(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double =
        10.0 * weightKg + 6.25 * heightCm - 5.0 * age +
            if (sex == Sex.MALE) 5.0 else -161.0

    /**
     * Katch-McArdle, the validated fat-free-mass equation: `370 + 21.6 * FFM`.
     * Fat mass and age are retained in the signature for source compatibility but do
     * not enter the published equation.
     */
    @Suppress("UNUSED_PARAMETER")
    fun fromComposition(ffmKg: Double, fatMassKg: Double, age: Int): Double =
        370.0 + 21.6 * ffmKg

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

    /**
     * Best available estimate for a profile. [peakKg] may be supplied from the real
     * weigh-in history; when null (or invalid) the current weight is used as the peak,
     * preserving the previous behaviour.
     */
    fun forProfile(
        profile: Profile,
        peakKg: Double? = null,
        inDeficit: Boolean = false,
    ): Double {
        val ffm = profile.fatFreeMassKg
        val fm = profile.fatMassKg
        val base = if (ffm != null && fm != null) fromComposition(ffm, fm, profile.age)
        else fromBody(profile.weightKg, profile.heightCm, profile.age, profile.sex)
        val effectivePeak = peakKg?.takeIf { it.isFinite() && it > 0.0 } ?: profile.weightKg
        return corrected(base, inDeficit, isBelowPeak(profile.weightKg, effectivePeak))
    }
}
