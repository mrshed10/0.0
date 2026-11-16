package com.tvremote.floating

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية الوصول: تُستخدم فقط لتنفيذ أوامر النظام (رجوع/رئيسية/الأخيرة)
 * ولتحويل الزر الأوسط للماوس إلى "رجوع" في كل النظام (إذا كان الجهاز يرسله كزر KeyEvent).
 *
 * ملاحظة: في معظم أجهزة أندرويد يصل الزر الأوسط كنقرة ماوس عادية لا يمكن لأي خدمة
 * التقاطها خارج نوافذها دون تعطيل حركة المؤشر، لذلك يعمل حينها على الأيقونة واللوحة العائمة.
 */
class ControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
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
