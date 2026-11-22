package com.metriq.data

import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.Profile
import com.metriq.core.ProteinTier
import com.metriq.core.Sex
import com.metriq.data.db.MetriqDatabase

class ProfileRepository(
    private val db: MetriqDatabase,
    private val notifier: DataChangeNotifier? = null,
) {

    fun get(): Profile? = db.metriqQueries.selectProfile().executeAsOneOrNull()?.let { row ->
        Profile(
            sex = runCatching { Sex.valueOf(row.sex) }.getOrDefault(Sex.MALE),
            age = row.age.toInt(),
            heightCm = row.heightCm,
            weightKg = row.weightKg,
            bodyFatPercent = row.bodyFatPercent,
            activityLevel = runCatching { ActivityLevel.valueOf(row.activity) }
                .getOrDefault(ActivityLevel.MODERATE),
            goal = runCatching { GoalType.valueOf(row.goal) }.getOrDefault(GoalType.MAINTAIN),
            diet = runCatching { DietStyle.valueOf(row.diet) }.getOrDefault(DietStyle.BALANCED),
            proteinTier = runCatching { ProteinTier.valueOf(row.proteinTier) }
                .getOrDefault(ProteinTier.MODERATE),
        )
    }

    fun save(profile: Profile, targetWeightKg: Double?) {
        db.metriqQueries.upsertProfile(
            profile.sex.name,
            profile.age.toLong(),
            profile.heightCm,
            profile.weightKg,
            profile.bodyFatPercent,
            profile.activityLevel.name,
            profile.goal.name,
            profile.diet.name,
            profile.proteinTier.name,
            targetWeightKg,
            System.currentTimeMillis(),
        )
        notifier?.notify(DataType.PROFILE)
    }
}
