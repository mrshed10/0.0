package com.livetranslate.overlay.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * خدمة إمكانية وصول لا تقرأ أي محتوى؛ وظيفتها الوحيدة توفير نافذة من النوع
 * TYPE_ACCESSIBILITY_OVERLAY. هذه النافذة "موثوقة" لدى النظام، فيمكن رسم الترجمة
 * بعتامة ‎100%‎ فوق النص الأصلي مع بقاء اللمس والتمرير يمرّان للتطبيق تحتها.
 * (نوافذ TYPE_APPLICATION_OVERLAY العادية مقيّدة بعتامة 0.8 على Android 12+ وإلا يُحجب اللمس.)
 */
class TranslateAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (instance === this) instance = null
        _connected.value = false
    }

    companion object {
        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** هل الخدمة مفعّلة في الإعدادات (حتى لو لم تتصل بعد). */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val name = "${context.packageName}/${TranslateAccessibilityService::class.java.name}"
            return enabled.split(':').any { it.equals(name, ignoreCase = true) }
        }
    }
}
