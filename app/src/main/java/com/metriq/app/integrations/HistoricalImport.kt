package com.metriq.app.integrations

import android.content.Context
import com.metriq.app.AppGraph
import com.metriq.app.DailyTraining
import com.metriq.app.TrainingHistory
import com.metriq.app.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One-shot backfill from Health Connect into the local repositories.
 *
 * Gap-only by design: a day that already has a local step entry, a local training
 * record or a local weigh-in is never touched. Manual entries therefore always win,
 * and repeated imports cannot create duplicates.
 */
object HistoricalImport {

    private const val DAYS = 30

    /** Returns the number of records written (step days + training days + weight samples). */
    suspend fun importFromHealthConnect(context: Context): Int = withContext(Dispatchers.IO) {
        AppGraph.ensure(context)
        val hc = AppGraph.healthConnect ?: HealthConnectManager(context.applicationContext)
        if (!hc.isAvailable() || !hc.hasAllPermissions()) return@withContext 0

        val today = LocalDate.now()
        val start = today.minusDays((DAYS - 1).toLong())
        var imported = 0

        // Weight: one sample per day, only where no local weigh-in exists.
        val localWeightDates = AppGraph.store.weights.all().map { it.date }.toSet()
        runCatching { hc.readWeight(DAYS) }.getOrDefault(emptyList())
            .filter { (date, _) -> date in start..today && date !in localWeightDates }
            .forEach { (date, kg) ->
                AppGraph.store.weights.add(date.toString(), kg, "health_connect")
                imported++
            }

        var day = start
        while (!day.isAfter(today)) {
            val key = day.toString()

            val steps = runCatching { hc.readSteps(day) }.getOrDefault(0L)
            if (steps > 0 && AppGraph.store.step.forDate(key) == null) {
                AppGraph.store.step.upsert(key, steps, "health_connect")
                imported++
            }

            if (TrainingHistory.forDate(day) == null) {
                val active = runCatching { hc.readActiveCalories(day) }.getOrDefault(0.0)
                val minutes = runCatching { hc.readExerciseMinutes(day) }.getOrDefault(0)
                val sessions = runCatching { hc.readExerciseSessions(day) }.getOrDefault(0)
                if (active > 0.0 || minutes > 0 || sessions > 0) {
                    TrainingHistory.record(DailyTraining(day, active, minutes, sessions))
                    imported++
                }
            }

            day = day.plusDays(1)
        }

        imported
    }
}
