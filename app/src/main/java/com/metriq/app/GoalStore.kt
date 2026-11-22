package com.metriq.app

import com.metriq.core.GoalOverride

/** Local persistence for the user's manually edited goal. No network, no telemetry. */
object GoalStore {

    fun read(): GoalOverride? = GoalOverrideCodec.decode(AppSettings.get(AppSettings.GOAL_OVERRIDE_JSON))

    fun write(goal: GoalOverride) {
        AppGraph.store.settings.set(AppSettings.GOAL_OVERRIDE_JSON, GoalOverrideCodec.encode(goal))
    }

    fun clear() {
        AppGraph.store.settings.set(AppSettings.GOAL_OVERRIDE_JSON, "")
    }
}
