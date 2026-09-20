package com.night.homira

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

object HomiraCrashReporter {
    private const val PREFS = "homira_crash_reporter"
    private const val KEY_REPORT = "last_crash_report"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))

                val report = buildString {
                    appendLine("Homira crash report")
                    appendLine("Time: ${Instant.now()}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(writer.toString())
                }

                appContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_REPORT, report.take(64_000))
                    .commit()
            }

            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun peek(context: Context): String? =
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REPORT, null)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_REPORT)
                .apply()
        }
    }
}
