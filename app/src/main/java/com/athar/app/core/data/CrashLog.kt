package com.athar.app.core.data

import android.content.Context
import android.os.Build
import com.athar.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the last crash's details so the user can see and share them from
 * Settings (the app has no internet, so nothing is sent anywhere).
 */
object CrashLog {
    private const val PREFS = "athar_crash"
    private const val KEY = "last"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = buildString {
                    appendLine("Athar ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine(LocalDateTime.now().toString())
                    appendLine("Thread: ${thread.name}")
                    append(trace.take(6_000))
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, report).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
