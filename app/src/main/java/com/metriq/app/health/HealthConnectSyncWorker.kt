package com.metriq.app.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.metriq.app.AppGraph
import com.metriq.app.DailyTraining
import com.metriq.app.TrainingHistory
import java.time.LocalDate

class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean("manual", false)
        if (!manual && !com.metriq.app.AppSettings.bool(com.metriq.app.AppSettings.HEALTH_AUTO_SYNC)) {
            return Result.success()
        }
        val hc = AppGraph.healthConnect ?: return Result.failure()
        if (!hc.isAvailable() || !hc.hasAllPermissions()) return Result.failure()

        val today = LocalDate.now()
        val settings = AppGraph.store.settings
        fun enabled(key: String) = settings.get(key)?.toBooleanStrictOrNull() ?: true

        // Steps: never overwrite a manual entry with an empty or older Health Connect reading.
        if (enabled("health_import_steps")) {
            val hcSteps = hc.readSteps(today)
            val local = AppGraph.store.step.range(today.toString(), today.toString()).firstOrNull()
            if (hcSteps > 0 && (local == null || local.source == "health_connect")) {
                AppGraph.store.step.upsert(today.toString(), hcSteps, "health_connect")
            }
        }

        // Weight: the source column arbitrates. Manual weigh-ins always win.
        if (enabled("health_import_weight")) {
            val localSources = AppGraph.store.db.metriqQueries.selectWeights()
                .executeAsList()
                .associate { it.date to it.source }
            hc.readWeight(7).forEach { (date, kg) ->
                val source = localSources[date.toString()]
                if (source == null || source == "health_connect") {
                    AppGraph.store.weights.add(date.toString(), kg, "health_connect")
                }
            }
        }

        // Calories and exercise feed the dashboard cards and the adaptive macro engine.
        val active = runCatching { hc.readActiveCalories(today) }.getOrDefault(0.0)
        val sessions = runCatching { hc.readExerciseSessions(today) }.getOrDefault(0)
        val minutes = runCatching { hc.readExerciseMinutes(today) }.getOrDefault(0)
        settings.set("health_active_calories", active.toString())
        settings.set("health_total_calories", runCatching { hc.readTotalCalories(today) }.getOrDefault(0.0).toString())
        settings.set("health_exercise_sessions", sessions.toString())
        TrainingHistory.record(DailyTraining(today, active, minutes, sessions))

        settings.set("health_last_sync", System.currentTimeMillis().toString())
        return Result.success()
    }
}
