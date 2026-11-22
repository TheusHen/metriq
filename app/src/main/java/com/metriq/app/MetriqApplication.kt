package com.metriq.app

import android.app.Application
import com.metriq.app.health.HealthConnectManager
import com.metriq.app.widget.WidgetRefreshWorker
import com.metriq.data.DatabaseFactory
import com.metriq.data.MetriqStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppGraph {
    lateinit var store: MetriqStore
        private set

    var healthConnect: HealthConnectManager? = null
        private set

    fun init(application: Application) {
        store = DatabaseFactory.create(application)
        healthConnect = HealthConnectManager(application)

        val work = WorkManager.getInstance(application)
        val minutes = AppSettings.get(AppSettings.WIDGET_REFRESH_MINUTES).toLongOrNull()
            ?.coerceIn(15L, 120L) ?: 30L
        work.enqueueUniquePeriodicWork(
            "widget_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(minutes, TimeUnit.MINUTES).build(),
        )
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            store.notifier.events.collect {
                // REPLACE coalesces bursts of local changes into a single refresh.
                work.enqueueUniqueWork(
                    "widget_refresh_now",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
                )
            }
        }
    }

    fun ensure(context: android.content.Context) {
        if (!::store.isInitialized) store = DatabaseFactory.create(context.applicationContext)
    }
}

class MetriqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        AppGraph.init(this)
    }
}
