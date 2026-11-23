package com.metriq.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.metriq.app.AppGraph

/** Posts one local reminder per day; no-ops unless the user opted in and granted permission. */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { AppGraph.store.settings.get(ReminderNotifications.ENABLED_KEY) }
            .getOrNull()
            ?.toBooleanStrictOrNull() ?: false
        if (!enabled || !ReminderNotifications.isPermissionGranted(applicationContext)) {
            return Result.success()
        }
        ReminderNotifications.post(applicationContext)
        return Result.success()
    }
}
