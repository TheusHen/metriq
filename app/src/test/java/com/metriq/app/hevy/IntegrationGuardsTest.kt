package com.metriq.app.hevy

import android.content.Context
import com.metriq.app.ai.DownloadPolicy
import com.metriq.app.health.WeightReading
import com.metriq.app.health.latestWeightByDate
import com.metriq.app.integrations.HistoricalImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class IntegrationGuardsTest {

    @Test
    fun `download rejects non-HTTPS URLs`() {
        assertNull(DownloadPolicy.urlError("https://example.com/model.bin"))
        assertNotNull(DownloadPolicy.urlError("http://example.com/model.bin"))
        assertNotNull(DownloadPolicy.urlError("file:///tmp/model.bin"))
        assertNotNull(DownloadPolicy.urlError("   "))
    }

    @Test
    fun `download budget is converted to bytes and enforced`() {
        assertEquals(250L * 1024L * 1024L, DownloadPolicy.budgetBytes(250))
        assertEquals(0L, DownloadPolicy.budgetBytes(0))
        assertFalse(DownloadPolicy.exceeds(100L, 100L))
        assertTrue(DownloadPolicy.exceeds(101L, 100L))
    }

    @Test
    fun `latest weight per day uses the record with the greatest time`() {
        val day = LocalDate.of(2026, 1, 1)
        val nextDay = day.plusDays(1)
        val readings = listOf(
            WeightReading(day, 80.0, Instant.parse("2026-01-01T08:00:00Z")),
            WeightReading(day, 79.0, Instant.parse("2026-01-01T20:00:00Z")),
            WeightReading(day, 81.0, Instant.parse("2026-01-01T09:00:00Z")),
            WeightReading(nextDay, 78.5, Instant.parse("2026-01-02T08:00:00Z")),
        )

        val result = latestWeightByDate(readings)

        assertEquals(2, result.size)
        assertEquals(79.0, result[day]!!, 0.0)
        assertEquals(78.5, result[nextDay]!!, 0.0)
    }

    // These two guards are static/API-level on purpose: WorkManager and Android
    // framework code cannot run in this module's local JVM test suite (no
    // instrumentation/Robolectric), but the wiring the wizard depends on can be
    // pinned here so a regression fails fast.

    @Test
    fun `health sync worker publishes a data change only after a successful sync`() {
        val source = sourceOf("app/src/main/java/com/metriq/app/health/HealthConnectSyncWorker.kt")
        assertTrue(
            "worker must import DataType from core-data",
            source.contains("com.metriq.data.DataType") || source.contains("com.metriq.data.*"),
        )
        val notify = "AppGraph.store.notifier.notify(DataType.HEALTH_CONNECT_SYNC)"
        assertTrue("worker must notify HEALTH_CONNECT_SYNC on success", source.contains(notify))
        val lastSync = source.indexOf("settings.set(\"health_last_sync\"")
        assertTrue("worker must write the last-sync timestamp", lastSync >= 0)
        assertTrue(
            "notification must be on the success path, after the last-sync write",
            source.indexOf(notify) > lastSync,
        )
        assertTrue(
            "enqueue(context, manual) and enqueueUniqueWork must stay stable",
            source.contains("fun enqueue(context: Context, manual: Boolean)") &&
                source.contains("enqueueUniqueWork("),
        )
    }

    @Test
    fun `historical import exposes a backfill helper for the connect wizard`() {
        val backfill = HistoricalImport::class.java.declaredMethods
            .firstOrNull { it.name == "backfillAfterConnect" }
        assertNotNull("HistoricalImport.backfillAfterConnect(context) must exist", backfill)
        assertEquals(Context::class.java, backfill!!.parameterTypes.firstOrNull())
        // Suspend functions take a Continuation as their final JVM parameter.
        assertEquals("kotlin.coroutines.Continuation", backfill.parameterTypes.lastOrNull()?.name)
    }

    /** Gradle runs local tests from the app module dir; walk up to the repo root. */
    private fun sourceOf(relativePath: String): String {
        var dir = File(".").absoluteFile
        repeat(5) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: return File(dir, relativePath).readText()
        }
        return File(dir, relativePath).readText()
    }
}
