package com.localcam.surveillance

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * كل ما يخص ملفات المقاطع: المجلد، التسمية، القائمة، والحذف التلقائي للأقدم.
 *
 * المجلد: Android/data/com.localcam.surveillance/files/Movies/Recordings
 * (مجلد خاص بالتطبيق، لا يحتاج أي إذن تخزين، ويُحذف عند إلغاء تثبيت التطبيق).
 */
object ClipStorage {

    private const val TAG = "ClipStorage"

    /** هامش أمان: نترك دائماً 200 ميجابايت فارغة في ذاكرة الجهاز. */
    private const val MIN_FREE_BYTES = 200L * 1024 * 1024

    /** Locale.US حتى تكون الأرقام في اسم الملف إنجليزية (وليست ٠١٢٣) مع لغة الجهاز العربية. */
    private fun nameFormat() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun clipsDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies") // احتياط نادر إن لم تتوفر الذاكرة الخارجية
        return File(base, "Recordings").apply { if (!exists()) mkdirs() }
    }

    /** ملف جديد باسم التاريخ والوقت، مثل: 2026-11-20_16-05-00.mp4 */
    fun newClipFile(context: Context): File {
        val dir = clipsDir(context)
        val base = nameFormat().format(Date())
        var file = File(dir, "$base.mp4")
        var i = 1
        while (file.exists()) { // في حال بدأ مقطعان في نفس الثانية
            file = File(dir, "${base}_$i.mp4")
            i++
        }
        return file
    }

    /** المقاطع مرتبة من الأحدث إلى الأقدم (الاسم يحتوي التاريخ، فالترتيب الأبجدي = الزمني). */
    fun listClips(context: Context): List<File> =
        clipsDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".mp4", ignoreCase = true) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun totalSize(context: Context): Long = listClips(context).sumOf { it.length() }

    /** تقدير حجم المقطع القادم حسب الجودة ومدته (تقريبي ومتحفّظ). */
    fun estimateSegmentBytes(quality: String, segmentMs: Long): Long {
        val megabitsPerSecond = when (quality) {
            AppSettings.QUALITY_SD -> 3L
            AppSettings.QUALITY_FHD -> 16L
            else -> 8L
        }
        return megabitsPerSecond * 1_000_000L / 8L * (segmentMs / 1000L)
    }

    data class CleanupResult(val deleted: List<String>, val enoughSpace: Boolean)

    /**
     * يُستدعى قبل بدء كل مقطع جديد:
     * يحذف أقدم المقاطع غير المقفلة حتى يتسع المجلد للمقطع القادم ضمن الحد الأقصى
     * الذي حدده المستخدم، وحتى تبقى مساحة كافية فعلياً في ذاكرة الجهاز.
     */
    fun ensureSpace(context: Context, segmentBytes: Long): CleanupResult {
        val settings = AppSettings(context)
        val limit = settings.maxStorageBytes
        val locked = settings.lockedClips()
        val dir = clipsDir(context)

        // لا نحجز أكثر من ثلث الحد، حتى لا نحذف كل شيء إن كان الحد صغيراً جداً
        val reserve = minOf(segmentBytes, limit / 3)

        val clips = listClips(context)
        var total = clips.sumOf { it.length() }
        val deleted = mutableListOf<String>()

        fun needMore() = total + reserve > limit || dir.usableSpace < reserve + MIN_FREE_BYTES

        // من الأقدم إلى الأحدث، مع تجاهل المقفل
        for (file in clips.asReversed()) {
            if (!needMore()) break
            if (file.name in locked) continue
            val size = file.length()
            if (file.delete()) {
                total -= size
                deleted += file.name
                Log.i(TAG, "حُذف تلقائياً: ${file.name}")
            }
        }
        return CleanupResult(deleted, !needMore())
    }

    /** حذف يدوي من قائمة المقاطع (يزيل علامة القفل أيضاً). */
    fun deleteClip(context: Context, file: File): Boolean {
        val ok = file.delete()
        if (ok) AppSettings(context).setLocked(file.name, false)
        return ok
    }
}
