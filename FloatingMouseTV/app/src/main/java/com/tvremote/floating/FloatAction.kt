package com.tvremote.floating

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.widget.Toast

/**
 * أزرار القائمة العائمة.
 * [closesPanel] = هل تُغلق القائمة بعد الضغط (أزرار الصوت تُبقيها مفتوحة لتكرار الضغط).
 */
enum class FloatAction(
    val labelRes: Int,
    val iconRes: Int,
    val needsAccessibility: Boolean,
    val closesPanel: Boolean
) {
    VOLUME_UP(R.string.act_volume_up, R.drawable.ic_volume_up, false, false),
    VOLUME_DOWN(R.string.act_volume_down, R.drawable.ic_volume_down, false, false),
    MUTE(R.string.act_mute, R.drawable.ic_volume_off, false, false),
    PLAY_PAUSE(R.string.act_play_pause, R.drawable.ic_play_pause, false, true),
    BACK(R.string.act_back, R.drawable.ic_back, true, true),
    HOME(R.string.act_home, R.drawable.ic_home, true, true),
    RECENTS(R.string.act_recents, R.drawable.ic_recents, true, true),
    SPLIT_SCREEN(R.string.act_split, R.drawable.ic_split, true, true);
}

/** تنفيذ الأوامر: الصوت والوسائط عبر AudioManager، وأوامر النظام عبر خدمة إمكانية الوصول */
object ActionRunner {

    private var mutedLegacy = false

    fun run(context: Context, action: FloatAction): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            when (action) {
                FloatAction.VOLUME_UP -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                FloatAction.VOLUME_DOWN -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                FloatAction.MUTE -> toggleMute(audio)
                FloatAction.PLAY_PAUSE -> {
                    audio.dispatchMediaKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    )
                    audio.dispatchMediaKeyEvent(
                        KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    )
                }
                FloatAction.BACK -> return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                FloatAction.HOME -> return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                FloatAction.RECENTS -> return global(context, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                // دخول/خروج تقسيم الشاشة (العودة للشاشة الكاملة)
                FloatAction.SPLIT_SCREEN -> return MultiWindow.toggleSplit(context)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun global(context: Context, globalAction: Int): Boolean {
        val service = ControlAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, R.string.need_accessibility, Toast.LENGTH_LONG).show()
            return false
        }
        return service.performGlobalAction(globalAction)
    }

    private fun toggleMute(audio: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI
            )
        } else {
            mutedLegacy = !mutedLegacy
            @Suppress("DEPRECATION")
            audio.setStreamMute(AudioManager.STREAM_MUSIC, mutedLegacy)
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI
            )
        }
    }
}
