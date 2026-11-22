package com.metriq.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Diagnostic instrumentation: persists the last uncaught exception so the user can
 * copy it from the UI on the next launch (no adb / logcat needed).
 */
object CrashLog {
    private const val TAG = "MetriqCrash"
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildString {
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
            }
            try {
                File(context.filesDir, FILE).writeText(report)
            } catch (_: Exception) {
            }
            Log.e(TAG, report)
            runCatching {
                context.startActivity(
                    Intent(context, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            previous?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(1)
        }
    }

    fun read(context: Context): String? = try {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }
}
