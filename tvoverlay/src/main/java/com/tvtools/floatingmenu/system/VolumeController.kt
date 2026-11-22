package com.tvtools.floatingmenu.system

import android.content.Context
import android.media.AudioManager

/** التحكم بصوت الوسائط (STREAM_MUSIC) وهو الصوت الذي يتحكم به ريموت التلفاز عادةً. */
class VolumeController(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val stream = AudioManager.STREAM_MUSIC

    val maxLevel: Int get() = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val currentLevel: Int get() = audio.getStreamVolume(stream)
    val isMuted: Boolean get() = audio.isStreamMute(stream) || currentLevel == 0

    /** true إذا كان الصوت ثابتاً (يُدار غالباً عبر HDMI-CEC أو مكبر خارجي). */
    val isFixed: Boolean get() = audio.isVolumeFixed

    val percent: Int get() = currentLevel * 100 / maxLevel

    // adjustStreamVolume أفضل من setStreamVolume على أجهزة التلفاز لأنه يمر عبر HDMI-CEC عند الحاجة.
    fun raise() = adjust(AudioManager.ADJUST_RAISE)

    fun lower() = adjust(AudioManager.ADJUST_LOWER)

    fun toggleMute() {
        if (isMuted && currentLevel == 0 && !audio.isStreamMute(stream)) {
            adjust(AudioManager.ADJUST_RAISE)
        } else {
            adjust(AudioManager.ADJUST_TOGGLE_MUTE)
        }
    }

    fun setLevel(level: Int) = safely {
        audio.setStreamVolume(stream, level.coerceIn(0, maxLevel), 0)
    }

    private fun adjust(direction: Int) = safely {
        audio.adjustStreamVolume(stream, direction, 0)
    }

    // قد يرمي النظام SecurityException في وضع "عدم الإزعاج".
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            // تجاهل بهدوء
        }
    }
}
