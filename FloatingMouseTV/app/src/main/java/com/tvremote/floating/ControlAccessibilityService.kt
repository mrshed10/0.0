package com.tvremote.floating

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية الوصول: تُستخدم فقط لتنفيذ أوامر النظام (رجوع/رئيسية/الأخيرة)
 * ولتحويل الزر الأوسط للماوس إلى "رجوع" في كل النظام.
 */
class ControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        configureMouseObserver()
        Log.i(TAG, "Accessibility service connected")
    }

    /**
     * Android 15+: نراقب أحداث الماوس (بدون حجبها عن التطبيقات) لالتقاط الزر الأوسط.
     * في الإصدارات الأقدم لا يرسل أندرويد نقرات الماوس لخدمات إمكانية الوصول.
     */
    fun configureMouseObserver() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        try {
            val info = serviceInfo ?: return
            val sources = if (Prefs.isMiddleBackEnabled(this)) InputDevice.SOURCE_MOUSE else 0
            info.setMotionEventSources(sources)
            info.setObservedMotionEventSources(sources)
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "configureMouseObserver failed", e)
        }
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
            event.actionButton == MotionEvent.BUTTON_TERTIARY &&
            Prefs.isMiddleBackEnabled(this)
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** بعض الأجهزة ترسل الزر الأوسط كزر (KeyEvent) — نستخدم الرمز الذي تعلّمه التطبيق */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.isMiddleBackEnabled(this)) return false
        val learned = Prefs.middleKeyCode(this)
        if (learned == -1 || event.keyCode != learned) return false
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ControlA11y"

        @Volatile
        var instance: ControlAccessibilityService? = null
            private set

        fun component(context: Context) =
            ComponentName(context, ControlAccessibilityService::class.java)

        /** هل الخدمة مفعّلة في إعدادات إمكانية الوصول؟ */
        fun isEnabled(context: Context): Boolean {
            val me = component(context)
            val list = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
