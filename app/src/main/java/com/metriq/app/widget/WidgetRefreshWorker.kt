package com.metriq.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(DailyMacroWidget::class.java)
            .forEach { DailyMacroWidget().update(applicationContext, it) }
        manager.getGlanceIds(QuickLogWidget::class.java)
            .forEach { QuickLogWidget().update(applicationContext, it) }
        manager.getGlanceIds(WeightWidget::class.java)
            .forEach { WeightWidget().update(applicationContext, it) }
        return Result.success()
    }
}
