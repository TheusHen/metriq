package com.metriq.core

/**
 * Thresholds shared by [MacroEngine] and [TrainingEngine].
 *
 * Both engines classify a day from the same raw signals; keeping the cutoffs
 * in one place prevents the two classifications from drifting apart.
 */
object TrainingThresholds {
    const val MIN_ACTIVE_KCAL = 5.0
    const val MIN_DURATION_MINUTES = 5
    const val LIGHT_MINUTES = 25
    const val MODERATE_MINUTES = 60
    const val LIGHT_KCAL_PER_KG = 3.0
    const val MODERATE_KCAL_PER_KG = 6.0
}
