package com.metriq.app.ai

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/** Lightweight, read-only device signals used to decide which (if any) model tier fits. */
object DeviceProfiler {

    /** Per-app heap budget in MB, as reported by the platform. */
    fun ramMb(context: Context): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.memoryClass ?: 0
    }

    /** Currently free storage in MB on the app's own files partition. */
    fun freeMb(context: Context): Int {
        return try {
            val stat = StatFs(context.filesDir.path)
            (stat.availableBytes / (1024L * 1024L)).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    /** Coarse capability bucket derived from the memory class. */
    fun score(context: Context): String {
        val ram = ramMb(context)
        return when {
            ram >= 300 -> "HIGH"
            ram >= 180 -> "MID"
            else -> "LOW"
        }
    }
}
