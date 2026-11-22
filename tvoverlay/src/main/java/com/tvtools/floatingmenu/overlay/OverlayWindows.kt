package com.tvtools.floatingmenu.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout

object OverlayWindows {

    /** نوع نافذة الظهور فوق التطبيقات حسب إصدار أندرويد. */
    @Suppress("DEPRECATION")
    val type: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    @Suppress("DEPRECATION")
    fun screenSize(windowManager: WindowManager): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also { windowManager.defaultDisplay.getRealSize(it) }
        }
}

/** جذر القائمة العائمة: يلتقط أزرار الكيبورد/الريموت (Esc، رجوع، M، +، -) قبل باقي العناصر. */
class KeyInterceptLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var keyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyHandler?.invoke(event) == true || super.dispatchKeyEvent(event)
}
