package com.tvremote.keyboard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * خدمة إمكانية الوصول التي تلتقط ضغطات الكيبورد الخارجي في كل التطبيقات
 * وتحوّلها إلى أوامر ريموت (صوت، رئيسية، رجوع، التطبيقات الأخيرة).
 */
class KeyboardRemoteService : AccessibilityService() {

    private enum class Action { VOLUME_UP, VOLUME_DOWN, MUTE, HOME, BACK, RECENTS }

    private lateinit var audioManager: AudioManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "Keyboard remote service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = mapKey(event.keyCode) ?: return false // زر غير مبرمج: يمر للتطبيق كالمعتاد

        // أزرار التنقّل داخل حقل نص (Backspace للحذف، Home لبداية السطر) تُترك للكتابة
        if (isTextEditingKey(event.keyCode) && isEditingText()) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val isRepeat = event.repeatCount > 0
            when (action) {
                // الصوت يتكرر عند الضغط المطوّل
                Action.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                Action.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                // باقي الأوامر تُنفَّذ مرة واحدة فقط لكل ضغطة
                Action.MUTE -> if (!isRepeat) toggleMute()
                Action.HOME -> if (!isRepeat) performGlobalAction(GLOBAL_ACTION_HOME)
                Action.BACK -> if (!isRepeat) performGlobalAction(GLOBAL_ACTION_BACK)
                Action.RECENTS -> if (!isRepeat) performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
        // نستهلك ACTION_DOWN و ACTION_UP معاً حتى لا يستقبل التطبيق المفتوح نصف ضغطة
        return true
    }

    private fun mapKey(keyCode: Int): Action? = when (keyCode) {
        KeyEvent.KEYCODE_F12 -> Action.VOLUME_UP
        KeyEvent.KEYCODE_F11 -> Action.VOLUME_DOWN
        KeyEvent.KEYCODE_F10 -> Action.MUTE
        KeyEvent.KEYCODE_F1,
        KeyEvent.KEYCODE_MOVE_HOME -> Action.HOME          // زر Home في الكيبورد
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_DEL -> Action.BACK                // DEL = Backspace
        KeyEvent.KEYCODE_F2 -> Action.RECENTS
        else -> null
    }

    private fun isTextEditingKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_MOVE_HOME

    /** هل يوجد حقل نص قابل للتحرير عليه التركيز الآن؟ */
    private fun isEditingText(): Boolean {
        var node: AccessibilityNodeInfo? = null
        return try {
            node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            node?.isEditable == true
        } catch (e: Exception) {
            false
        } finally {
            @Suppress("DEPRECATION")
            node?.recycle()
        }
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        } else {
            // Android 5.x لا يدعم ADJUST_TOGGLE_MUTE
            mutedLegacy = !mutedLegacy
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mutedLegacy)
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج أحداث الواجهة؛ الخدمة تعمل عبر onKeyEvent فقط
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "KeyboardRemote"
        private var mutedLegacy = false
    }
}
