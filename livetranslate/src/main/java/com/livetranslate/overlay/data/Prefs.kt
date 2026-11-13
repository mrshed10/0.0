package com.livetranslate.overlay.data

import android.content.Context
import com.livetranslate.overlay.translate.Languages

/** إعدادات بسيطة محفوظة محلياً. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("live_translate", Context.MODE_PRIVATE)

    var source: String
        get() = sp.getString(KEY_SOURCE, Languages.AUTO) ?: Languages.AUTO
        set(value) = sp.edit().putString(KEY_SOURCE, value).apply()

    var target: String
        get() = sp.getString(KEY_TARGET, "ar") ?: "ar"
        set(value) = sp.edit().putString(KEY_TARGET, value).apply()

    var glowAnimated: Boolean
        get() = sp.getBoolean(KEY_GLOW, true)
        set(value) = sp.edit().putBoolean(KEY_GLOW, value).apply()

    /** مركز الفقاعة أفقياً وحافتها السفلية (بالبكسل)، أو -1 للوضع الافتراضي. */
    var pillCenterX: Int
        get() = sp.getInt(KEY_PILL_X, -1)
        set(value) = sp.edit().putInt(KEY_PILL_X, value).apply()

    var pillBottom: Int
        get() = sp.getInt(KEY_PILL_BOTTOM, -1)
        set(value) = sp.edit().putInt(KEY_PILL_BOTTOM, value).apply()

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_GLOW = "glow_animated"
        const val KEY_PILL_X = "pill_cx"
        const val KEY_PILL_BOTTOM = "pill_bottom"
    }
}
