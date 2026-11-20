package com.localcam.surveillance

import android.content.Context

/**
 * الإعدادات المحفوظة محلياً (SharedPreferences) — لا حسابات ولا سحابة.
 * تشمل أيضاً قائمة أسماء المقاطع المقفلة (المحمية من الحذف التلقائي).
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** الحد الأقصى لمساحة التسجيلات بالجيجابايت (الافتراضي 10). */
    var maxStorageGb: Float
        get() = prefs.getFloat(KEY_MAX_GB, 10f)
        set(value) = prefs.edit().putFloat(KEY_MAX_GB, value).apply()

    val maxStorageBytes: Long
        get() = (maxStorageGb.toDouble() * 1024 * 1024 * 1024).toLong()

    /** مدة المقطع الواحد بالدقائق (الافتراضي 5). */
    var segmentMinutes: Int
        get() = prefs.getInt(KEY_SEGMENT_MIN, 5)
        set(value) = prefs.edit().putInt(KEY_SEGMENT_MIN, value).apply()

    /** الجودة: SD أو HD أو FHD. */
    var quality: String
        get() = prefs.getString(KEY_QUALITY, QUALITY_HD) ?: QUALITY_HD
        set(value) = prefs.edit().putString(KEY_QUALITY, value).apply()

    var useFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_FRONT, false)
        set(value) = prefs.edit().putBoolean(KEY_FRONT, value).apply()

    var recordAudio: Boolean
        get() = prefs.getBoolean(KEY_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO, value).apply()

    /** هل عُرض تنبيه البطارية الإرشادي من قبل؟ */
    var batteryTipShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_TIP, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_TIP, value).apply()

    // ---------- المقاطع المقفلة ----------

    fun lockedClips(): Set<String> =
        prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun isLocked(fileName: String): Boolean = fileName in lockedClips()

    fun setLocked(fileName: String, locked: Boolean) {
        val set = lockedClips().toMutableSet()
        if (locked) set.add(fileName) else set.remove(fileName)
        // نحفظ نسخة جديدة دائماً (شرط من SharedPreferences لمجموعات النصوص)
        prefs.edit().putStringSet(KEY_LOCKED, HashSet(set)).apply()
    }

    companion object {
        const val QUALITY_SD = "SD"
        const val QUALITY_HD = "HD"
        const val QUALITY_FHD = "FHD"

        private const val KEY_MAX_GB = "max_storage_gb"
        private const val KEY_SEGMENT_MIN = "segment_minutes"
        private const val KEY_QUALITY = "quality"
        private const val KEY_FRONT = "front_camera"
        private const val KEY_AUDIO = "record_audio"
        private const val KEY_BATTERY_TIP = "battery_tip_shown"
        private const val KEY_LOCKED = "locked_clips"
    }
}
