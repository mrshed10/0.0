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
    }
}
