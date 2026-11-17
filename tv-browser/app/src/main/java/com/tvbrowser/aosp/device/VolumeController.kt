package com.tvbrowser.aosp.device

import android.content.Context
import android.media.AudioManager
import android.os.Build
import org.json.JSONObject

/**
 * Device volume control (STREAM_MUSIC = what web video/audio uses).
 *
 * On HDMI-CEC boxes whose output volume is "fixed", AudioService forwards
 * adjustStreamVolume() to the TV/AVR over CEC, so the same calls still work.
 */
class VolumeController(context: Context) {

    data class State(val level: Int, val max: Int, val muted: Boolean, val fixed: Boolean) {
        val percent: Int get() = if (max <= 0) 0 else (level * 100 + max / 2) / max
        fun toJson(): JSONObject = JSONObject()
            .put("level", level).put("max", max).put("percent", percent)
            .put("muted", muted).put("fixed", fixed)
    }

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stream = AudioManager.STREAM_MUSIC

    /** Notified after every change (used for the on-screen volume indicator). */
    var listener: ((State) -> Unit)? = null

    /** Show Android's own volume panel too (many AOSP TV builds have none, so we draw ours). */
    var showSystemUi = false

    private val flags get() = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0

    fun state(): State {
        val muted = if (Build.VERSION.SDK_INT >= 23) am.isStreamMute(stream) else false
        return State(am.getStreamVolume(stream), am.getStreamMaxVolume(stream), muted, am.isVolumeFixed)
    }

    fun up(): State = adjust(AudioManager.ADJUST_RAISE)

    fun down(): State = adjust(AudioManager.ADJUST_LOWER)

    fun toggleMute(): State {
        if (Build.VERSION.SDK_INT >= 23) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, flags)
        } else {
            @Suppress("DEPRECATION")
            am.setStreamMute(stream, state().level > 0)
        }
        return publish()
    }

    fun setPercent(percent: Int): State {
        val max = am.getStreamMaxVolume(stream)
        val level = ((percent.coerceIn(0, 100) * max) + 50) / 100
        am.setStreamVolume(stream, level, flags)
        return publish()
    }

    private fun adjust(direction: Int): State {
        am.adjustStreamVolume(stream, direction, flags)
        return publish()
    }

    private fun publish(): State = state().also { listener?.invoke(it) }
}
