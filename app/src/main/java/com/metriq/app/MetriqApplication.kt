package com.metriq.app

import android.app.Application
import com.metriq.app.hevy.HevyRepository
import com.metriq.app.health.HealthConnectManager
import com.metriq.app.health.HealthConnectSyncWorker
import com.metriq.app.notifications.ReminderNotifications
import com.metriq.app.ui.common.FoodPackUrls
import com.metriq.app.widget.WidgetRefreshWorker
import com.metriq.data.DatabaseFactory
import com.metriq.data.FoodPackManager
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

    private lateinit var appContext: android.content.Context

    /** One process-wide repository so every caller sees the same decoded cache. */
    val hevyRepository: HevyRepository by lazy { HevyRepository(appContext) }

    fun init(application: Application) {
        appContext = application.applicationContext
        store = DatabaseFactory.create(application)
        configureFoodPacks()
        healthConnect = HealthConnectManager(application)

        val work = WorkManager.getInstance(application)
        val minutes = AppSettings.get(AppSettings.WIDGET_REFRESH_MINUTES).toLongOrNull()
            ?.coerceIn(15L, 120L) ?: 30L
        work.enqueueUniquePeriodicWork(
            "widget_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(minutes, TimeUnit.MINUTES).build(),
        )

        // Daily auto-sync. The worker itself checks HEALTH_AUTO_SYNC, so the toggle keeps
        // working without re-enqueuing; KEEP avoids stacking duplicates on every start.
        HealthConnectSyncWorker.enqueuePeriodic(application)

        // Local daily reminder; the worker checks notifications_enabled + runtime permission.
        ReminderNotifications.createChannel(application)
        ReminderNotifications.enqueueDaily(application)

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
        if (!::appContext.isInitialized) appContext = context.applicationContext
        if (!::store.isInitialized) {
            store = DatabaseFactory.create(context.applicationContext)
            configureFoodPacks()
        }
    }

    /**
     * The store's default manager is built in core-data, which must not hardcode an
     * app-owned release tag. Point the global manager at the current month (or at the
     * user's manual override) so every caller sees the same URL as FoodPackScreen.
     */
    private fun configureFoodPacks() {
        store.foodPacks = FoodPackManager(
            store.food,
            store.settings,
            FoodPackUrls.resolveBaseUrl(AppSettings.get(AppSettings.FOOD_PACK_BASE_URL)),
        )
    }
}

class MetriqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // CrashActivity runs in :crash; WorkManager/DatabaseFactory are main-process only.
        if (!CrashLog.isMainProcess(this)) return
        AppGraph.init(this)
    }
}
