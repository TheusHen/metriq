package com.metriq.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Local-only daily reminder. No remote pushes and no network: WorkManager wakes a worker
 * once a day, and the worker posts a notification only when the user has opted in AND the
 * runtime notification permission is granted.
 */
object ReminderNotifications {

    /** Settings key for the opt-in. Default is OFF (missing value is treated as false). */
    const val ENABLED_KEY = "notifications_enabled"

    const val CHANNEL_ID = "metriq_reminders"

    /** Exact runtime permission the UI must request (Android 13+ / API 33+). */
    const val PERMISSION: String = "android.permission.POST_NOTIFICATIONS"

    const val UNIQUE_WORK_NAME = "metriq_daily_reminder"

    private const val NOTIFICATION_ID = 4101

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metriq reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily weigh-in and weekly check-in reminders"
        }
        manager.createNotificationChannel(channel)
    }

    fun isPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /** Idempotent: KEEP means an existing schedule is never replaced on every app start. */
    fun enqueueDaily(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS, 6, TimeUnit.HOURS)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Posts the reminder if (and only if) the permission is currently granted. */
    fun post(context: Context) {
        if (!isPermissionGranted(context)) return
        createChannel(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val body = "How was today's weigh-in and this week's check-in?"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Metriq reminder")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }
}
