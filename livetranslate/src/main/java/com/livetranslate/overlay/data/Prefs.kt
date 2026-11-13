package com.livetranslate.overlay.data

import android.content.Context
import android.content.SharedPreferences
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

    /** الزر العائم على حافة الشاشة. */
    var bubbleEnabled: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, true)
        set(value) = sp.edit().putBoolean(KEY_BUBBLE, value).apply()

    var bubbleOnRight: Boolean
        get() = sp.getBoolean(KEY_BUBBLE_RIGHT, true)
        set(value) = sp.edit().putBoolean(KEY_BUBBLE_RIGHT, value).apply()

    var bubbleY: Int
        get() = sp.getInt(KEY_BUBBLE_Y, -1)
        set(value) = sp.edit().putInt(KEY_BUBBLE_Y, value).apply()

    fun observeLanguages(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SOURCE || key == KEY_TARGET) onChange()
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_BUBBLE = "bubble"
        const val KEY_BUBBLE_RIGHT = "bubble_right"
        const val KEY_BUBBLE_Y = "bubble_y"
    }
}
