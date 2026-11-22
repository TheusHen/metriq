package com.metriq.app

import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.Profile
import com.metriq.core.ProteinTier
import com.metriq.core.Sex

object DefaultProfile {
    val PROFILE = Profile(
        sex = Sex.MALE,
        age = 30,
        heightCm = 178.0,
        weightKg = 80.0,
        bodyFatPercent = null,
        activityLevel = ActivityLevel.MODERATE,
        goal = GoalType.MAINTAIN,
        diet = DietStyle.BALANCED,
        proteinTier = ProteinTier.MODERATE,
    )
}
