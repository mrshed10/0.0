package com.tvbrowser.aosp.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash protection:
 *  1. Writes any uncaught Java crash to a file, shown to the user on the next start
 *     (and in Settings → Diagnostics) so problems on the TV can be reported.
 *  2. Detects a crash loop: if the previous launch died before becoming stable,
 *     the next launch starts in SAFE MODE (tabs are not restored, Quick Access is shown),
 *     so a heavy page cannot crash the browser again and again.
 *  3. Keeps the last few "soft" errors that were caught by [safe].
 */
object CrashGuard {

    private const val FILE_NEW = "crash_new.txt"
    private const val FILE_SEEN = "crash_last.txt"
    private const val SP = "crashguard"
    private const val KEY_STARTING = "starting"

    private lateinit var appContext: Context
    private val softErrors = ArrayDeque<String>()

    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(appContext.filesDir, FILE_NEW).writeText(report(thread.name, error))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun report(thread: String, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            append(time).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" · ").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
            append("thread: ").append(thread).append("\n\n")
            append(sw.toString().take(12_000))
        }
    }

    fun recordSoftError(tag: String, t: Throwable) {
        synchronized(softErrors) {
            softErrors.addLast("[$tag] ${t.javaClass.simpleName}: ${t.message}")
            while (softErrors.size > 10) softErrors.removeFirst()
        }
    }

    fun softErrors(): List<String> = synchronized(softErrors) { softErrors.toList() }

    /** Returns a new (not yet shown) crash report and marks it as seen. */
    fun takeNewReport(context: Context): String? {
        val f = File(context.filesDir, FILE_NEW)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.renameTo(File(context.filesDir, FILE_SEEN)) }
        return text
    }

    fun lastReport(context: Context): String? {
        val f = File(context.filesDir, FILE_SEEN).takeIf { it.exists() } ?: File(context.filesDir, FILE_NEW)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    /**
     * Call at the very start of MainActivity. Returns true when the previous launch did not
     * reach [markStable] — i.e. it was killed or crashed (Java OR native engine crash).
     */
    fun beginLaunch(context: Context): Boolean {
        val sp = context.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val previousUnstable = sp.getBoolean(KEY_STARTING, false)
        sp.edit().putBoolean(KEY_STARTING, true).commit()
        return previousUnstable
    }

    fun markStable(context: Context) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putBoolean(KEY_STARTING, false).apply()
    }
}
