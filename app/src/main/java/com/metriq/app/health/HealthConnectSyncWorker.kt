package com.metriq.app.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metriq.app.AppGraph
import com.metriq.app.DailyTraining
import com.metriq.app.TrainingHistory
import com.metriq.data.DataType
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
        val hc = AppGraph.healthConnect ?: return failed("Health Connect não disponível")
        if (!hc.isAvailable() || !hc.hasAllPermissions()) return failed("Permissões do Health Connect ausentes")

        val today = LocalDate.now()
        val settings = AppGraph.store.settings
        fun enabled(key: String) = settings.get(key)?.toBooleanStrictOrNull() ?: true

        // Steps: never overwrite a manual entry with an empty or older Health Connect reading.
        if (enabled("health_import_steps")) {
            val hcSteps = runCatching { hc.readSteps(today) }
                .getOrElse { return failed("Falha ao ler passos: ${describe(it)}") }
            val local = AppGraph.store.step.range(today.toString(), today.toString()).firstOrNull()
            if (hcSteps > 0 && (local == null || local.source == "health_connect")) {
                AppGraph.store.step.upsert(today.toString(), hcSteps, "health_connect")
            }
        }

        // Weight: the source column arbitrates. Manual weigh-ins always win.
        // Health Connect may hold several samples for a day: only the newest one may be written.
        if (enabled("health_import_weight")) {
            val readings = runCatching { hc.readWeight(7) }
                .getOrElse { return failed("Falha ao ler peso: ${describe(it)}") }
            val localSources = AppGraph.store.db.metriqQueries.selectWeights()
                .executeAsList()
                .associate { it.date to it.source }
            latestWeightByDate(readings).forEach { (date, kg) ->
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
        // Even a settings-only sync (no new step/weight rows) changed derived health
        // data, so wake the dashboard/health cards including their goal recalculation.
        AppGraph.store.notifier.notify(DataType.HEALTH_CONNECT_SYNC)
        return Result.success()
    }

    private fun failed(message: String): Result = Result.failure(
        Data.Builder().putString("error", message.take(160)).build(),
    )

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }?.take(120) ?: error::class.simpleName.orEmpty()

    companion object {
        const val UNIQUE_WORK_NAME = "health_sync"

        /** Single entry point for all three UI call sites; REPLACE coalesces rapid taps. */
        fun enqueue(context: Context, manual: Boolean) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                    .setInputData(Data.Builder().putBoolean("manual", manual).build())
                    .build(),
            )
        }
    }
}
