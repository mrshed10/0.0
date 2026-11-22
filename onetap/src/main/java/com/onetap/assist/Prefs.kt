package com.onetap.assist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** What a tap on the floating button does first. */
enum class TapAction { ASSIST, VOICE, SEARCH, FALLBACK_ONLY }

/** Thin wrapper over SharedPreferences: tiny, synchronous, and observable by the overlay service. */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var sizeDp: Int
        get() = sp.getInt(K_SIZE, DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE)
        set(v) = sp.edit { putInt(K_SIZE, v.coerceIn(MIN_SIZE, MAX_SIZE)) }

    /** 0.2 (very transparent) .. 1.0 (opaque). */
    var opacity: Float
        get() = sp.getFloat(K_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, 1f)
        set(v) = sp.edit { putFloat(K_OPACITY, v.coerceIn(MIN_OPACITY, 1f)) }

    var snapToEdge: Boolean
        get() = sp.getBoolean(K_SNAP, true)
        set(v) = sp.edit { putBoolean(K_SNAP, v) }

    var autoHideScreenOff: Boolean
        get() = sp.getBoolean(K_AUTOHIDE, true)
        set(v) = sp.edit { putBoolean(K_AUTOHIDE, v) }

    var haptics: Boolean
        get() = sp.getBoolean(K_HAPTICS, true)
        set(v) = sp.edit { putBoolean(K_HAPTICS, v) }

    var tapAction: TapAction
        get() = runCatching { TapAction.valueOf(sp.getString(K_ACTION, null)!!) }
            .getOrDefault(TapAction.ASSIST)
        set(v) = sp.edit { putString(K_ACTION, v.name) }

    var autoFallback: Boolean
        get() = sp.getBoolean(K_AUTO_FALLBACK, true)
        set(v) = sp.edit { putBoolean(K_AUTO_FALLBACK, v) }

    /** Fallback target serialized with Intent.toUri(Intent.URI_INTENT_SCHEME). */
    val fallbackUri: String? get() = sp.getString(K_FALLBACK_URI, null)
    val fallbackLabel: String? get() = sp.getString(K_FALLBACK_LABEL, null)

    fun setFallback(uri: String?, label: String?) = sp.edit {
        if (uri == null) {
            remove(K_FALLBACK_URI); remove(K_FALLBACK_LABEL)
        } else {
            putString(K_FALLBACK_URI, uri); putString(K_FALLBACK_LABEL, label)
        }
    }

    /** Last position as a fraction of the free area, so it survives rotation and size changes. */
    var posXFrac: Float
        get() = sp.getFloat(K_POS_X, 1f)
        set(v) = sp.edit { putFloat(K_POS_X, v.coerceIn(0f, 1f)) }

    var posYFrac: Float
        get() = sp.getFloat(K_POS_Y, 0.35f)
        set(v) = sp.edit { putFloat(K_POS_Y, v.coerceIn(0f, 1f)) }

    companion object {
        private const val FILE = "onetap_prefs"
        const val K_SIZE = "size_dp"
        const val K_OPACITY = "opacity"
        const val K_SNAP = "snap_to_edge"
        const val K_AUTOHIDE = "auto_hide_screen_off"
        const val K_HAPTICS = "haptics"
        const val K_ACTION = "tap_action"
        const val K_AUTO_FALLBACK = "auto_fallback"
        const val K_FALLBACK_URI = "fallback_uri"
        const val K_FALLBACK_LABEL = "fallback_label"
        const val K_POS_X = "pos_x_frac"
        const val K_POS_Y = "pos_y_frac"

        const val MIN_SIZE = 40
        const val MAX_SIZE = 96
        const val DEFAULT_SIZE = 56
        const val MIN_OPACITY = 0.2f
        const val DEFAULT_OPACITY = 0.7f
    }
}
