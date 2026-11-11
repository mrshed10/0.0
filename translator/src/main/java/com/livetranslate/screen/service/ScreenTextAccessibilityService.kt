package com.livetranslate.screen.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.SourceBlock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class UiChangeKind { NAVIGATION, SCROLL, CONTENT }

data class UiChange(val kind: UiChangeKind, val packageName: String?)

/**
 * خدمة إمكانية الوصول: تلتقط شجرة العناصر النصية (UI Node Hierarchy) فور تغيّرها،
 * وتبث أحداث التنقل/التمرير/تغيّر المحتوى إلى خط الترجمة الحي.
 * أسرع وأقل استهلاكاً بكثير من التقاط الصور الدائم.
 */
class ScreenTextAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (pkg == packageName) return // تجاهل طبقاتنا
        val kind = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> UiChangeKind.NAVIGATION
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> UiChangeKind.SCROLL
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> UiChangeKind.CONTENT
            else -> return
        }
        _events.tryEmit(UiChange(kind, pkg))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        _connected.value = false
        super.onDestroy()
    }

    /**
     * لقطة لكل النصوص الظاهرة للمستخدم ضمن [region] بإحداثيات الشاشة.
     * تُستبعد نوافذنا ولوحة المفاتيح، والعناصر المحجوبة بنوافذ أعلى (مثل الحوارات).
     */
    fun snapshot(region: Box, excludeSystemUi: Boolean, limit: Int = 1500): List<SourceBlock> {
        val out = ArrayList<SourceBlock>()
        val occluders = ArrayList<Box>()
        val rect = Rect()
        val winList = runCatching { windows }.getOrNull().orEmpty()
        for (w in winList) { // مرتبة من الأعلى إلى الأسفل (Z-order)
            if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            ) continue
            val root = runCatching { w.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()
            if (pkg == packageName) continue
            if (excludeSystemUi && pkg == "com.android.systemui") continue
            w.getBoundsInScreen(rect)
            val winBox = Box(rect.left, rect.top, rect.right, rect.bottom)
            collect(root, region, occluders, out, limit, rect)
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && !winBox.isEmpty) occluders += winBox
            if (out.size >= limit) break
        }
        return out
    }

    private fun collect(
        root: AccessibilityNodeInfo,
        region: Box,
        occluders: List<Box>,
        out: MutableList<SourceBlock>,
        limit: Int,
        rect: Rect,
    ) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && out.size < limit && visited < 4000) {
            val (node, depth) = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            node.getBoundsInScreen(rect)
            val box = Box(rect.left, rect.top, rect.right, rect.bottom)
            if (box.isEmpty || !box.intersects(region)) continue
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && !node.isEditable && !node.isPassword) {
                val hidden = occluders.any { it.contains(box) }
                // ضمن المربع العائم: نأخذ العنصر إذا كان نصفه على الأقل داخل المنطقة
                if (!hidden && box.coverageBy(region) >= 0.5f) out += SourceBlock(text.take(2000), box)
                continue // النص في هذه العقدة يمثل أبناءها (TextView/زر)
            }
            if (depth > 80) continue
            for (i in node.childCount - 1 downTo 0) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                stack.addLast(child to depth + 1)
            }
        }
    }

    companion object {
        @Volatile var instance: ScreenTextAccessibilityService? = null
            private set

        private val _events = MutableSharedFlow<UiChange>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val events: SharedFlow<UiChange> = _events

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val cn = ComponentName(context, ScreenTextAccessibilityService::class.java)
            return enabled.split(':').any {
                it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
            }
        }
    }
}
