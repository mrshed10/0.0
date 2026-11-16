package com.tvremote.keyboard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * نافذة عائمة فوق كل التطبيقات، تُبقي عملية التطبيق بأولوية عالية جداً
 * (يعاملها النظام كواجهة ظاهرة للمستخدم) فلا يغلقها قاتل الذاكرة ولا معظم منظّفات الـ TV Box.
 *
 * - الوضع المخفي (دائماً): بكسل واحد شفاف، لا يحجب الشاشة ولا يستقبل اللمس.
 * - الوضع الظاهر (اختياري): أيقونة كيبورد صغيرة شبه شفافة في زاوية الشاشة.
 *
 * يحتاج صلاحية "الظهور فوق التطبيقات"؛ إن لم تتوفر نستخدم نافذة إمكانية الوصول
 * (TYPE_ACCESSIBILITY_OVERLAY) التي لا تحتاج صلاحية لكن حمايتها أضعف.
 */
class FloatingOverlay(private val service: Context) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        hide()
        try {
            val visible = isIconEnabled(service)
            val density = service.resources.displayMetrics.density

            val v: View = if (visible) {
                ImageView(service).apply {
                    setImageResource(R.drawable.ic_launcher)
                    alpha = 0.55f
                }
            } else {
                View(service).apply { alpha = 0.01f }
            }

            val size = if (visible) (40 * density).toInt() else 1
            val params = WindowManager.LayoutParams(
                size, size,
                windowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = if (visible) (16 * density).toInt() else 0
                y = if (visible) (16 * density).toInt() else 0
                title = "KeyboardRemoteOverlay"
            }

            windowManager.addView(v, params)
            view = v
        } catch (e: Exception) {
            // لا نسمح بأي انهيار داخل خدمة إمكانية الوصول
            Log.w(TAG, "overlay show failed", e)
            view = null
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            windowManager.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "overlay hide failed", e)
        }
        view = null
    }

    private fun windowType(): Int = when {
        // أقوى حماية: نافذة "فوق التطبيقات" بصلاحية SYSTEM_ALERT_WINDOW
        canDrawOverlays(service) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        canDrawOverlays(service) ->
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        // بديل بدون صلاحية (Android 5.1+)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 ->
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else ->
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    companion object {
        private const val TAG = "FloatingOverlay"
        private const val PREFS = "overlay"
        private const val KEY_ICON = "show_icon"

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun isIconEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ICON, false)

        fun setIconEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ICON, enabled).apply()
        }
    }
}
