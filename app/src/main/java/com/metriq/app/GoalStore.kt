package com.metriq.app

import com.metriq.core.GoalOverride
import org.json.JSONObject

/** Local persistence for the user's manually edited goal. No network, no telemetry. */
object GoalStore {

    fun read(): GoalOverride? {
        val raw = AppSettings.get(AppSettings.GOAL_OVERRIDE_JSON)
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            GoalOverride(
                dailyKcal = json.optInt("dailyKcal", 0),
                targetWeightKg = if (json.isNull("targetWeightKg")) null else json.optDouble("targetWeightKg"),
                weeks = if (json.isNull("weeks")) null else json.optInt("weeks", 0),
                reason = json.optString("reason").ifBlank { null },
            ).takeIf { it.dailyKcal > 0 }
        }.getOrNull()
    }

    fun write(goal: GoalOverride) {
        val json = JSONObject().apply {
            put("dailyKcal", goal.dailyKcal)
            put("targetWeightKg", goal.targetWeightKg ?: JSONObject.NULL)
            put("weeks", goal.weeks ?: JSONObject.NULL)
            put("reason", goal.reason.orEmpty())
        }
        AppGraph.store.settings.set(AppSettings.GOAL_OVERRIDE_JSON, json.toString())
    }

    fun clear() {
        AppGraph.store.settings.set(AppSettings.GOAL_OVERRIDE_JSON, "")
    }
}
