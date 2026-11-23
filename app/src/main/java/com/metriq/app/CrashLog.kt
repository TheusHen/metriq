package com.metriq.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Diagnostic instrumentation: persists the last uncaught exception so the user can
 * copy it from the UI on the next launch (no adb / logcat needed).
 */
object CrashLog {
    private const val TAG = "MetriqCrash"
    private const val FILE = "last_crash.txt"

    /** Only the main process may write a report or start the crash UI process. */
    fun isMainProcess(context: Context): Boolean {
        val myPid = Process.myPid()
        val processes = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.runningAppProcesses
        }.getOrNull()
        val name = processes?.firstOrNull { it.pid == myPid }?.processName ?: return true
        return name == context.packageName
    }

    fun install(context: Context) {
        if (!isMainProcess(context)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val file = File(context.filesDir, FILE)
            // Never let a secondary crash replace the original report with its own stack.
            if (!file.exists()) {
                val report = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.name}")
                    appendLine("Message: ${throwable.message}")
                    appendLine()
                    appendLine(Log.getStackTraceString(throwable))
                }
                try {
                    file.writeText(report)
                } catch (_: Exception) {
                }
            }
            Log.e(TAG, "Uncaught exception in ${thread.name}: ${throwable.javaClass.name}")
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
