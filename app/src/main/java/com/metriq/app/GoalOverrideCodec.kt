package com.metriq.app

import com.metriq.core.GoalOverride
import org.json.JSONObject

/** Pure JSON codec for the saved goal override, testable without Android. */
internal object GoalOverrideCodec {
    fun decode(raw: String): GoalOverride? {
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            GoalOverride(
                dailyKcal = json.optInt("dailyKcal", 0),
                targetWeightKg = if (json.isNull("targetWeightKg")) null else json.optDouble("targetWeightKg"),
                weeks = if (json.isNull("weeks")) null else json.optInt("weeks", 0),
                reason = json.optString("reason").ifBlank { null },
                // Backward compatibility: legacy JSON predates this field, so a missing
                // value must decode to a manual (fixed) goal.
                followExpenditure = json.optBoolean("followExpenditure", false),
            ).takeIf { it.dailyKcal > 0 }
        }.getOrNull()
    }

    fun encode(goal: GoalOverride): String = JSONObject().apply {
        put("dailyKcal", goal.dailyKcal)
        put("targetWeightKg", goal.targetWeightKg ?: JSONObject.NULL)
        put("weeks", goal.weeks ?: JSONObject.NULL)
        put("reason", goal.reason.orEmpty())
        put("followExpenditure", goal.followExpenditure)
    }.toString()
}
