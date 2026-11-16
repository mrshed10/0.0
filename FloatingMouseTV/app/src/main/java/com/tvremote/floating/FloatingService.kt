package com.tvremote.floating

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * خدمة أمامية حقيقية (Foreground Service + إشعار دائم صامت + START_STICKY)
 * تعرض أيقونة دائرية عائمة قابلة للسحب، وعند النقر تفتح لوحة تحكم زجاجية داكنة.
 */
class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    // ───────────────────────── دورة الحياة ─────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
        startForegroundSafely()
        handler.post(watchdog)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely() // مطلوب خلال 5 ثوانٍ من startForegroundService

        if (intent?.action == ACTION_STOP) {
            Prefs.setFloatingEnabled(this, false)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays(this)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && panel == null) showBubble()
        return START_STICKY // إن أغلق النظام الخدمة يعيد تشغيلها تلقائياً
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // إغلاق التطبيق من قائمة التطبيقات الأخيرة لا يوقف القائمة العائمة
        if (Prefs.isFloatingEnabled(this)) scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        removePanel()
        instance = null
        if (Prefs.isFloatingEnabled(this)) scheduleRestart() // أُغلقت رغماً عنا: نعود
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ───────────────────────── الأيقونة الدائرية ─────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        removeBubble()
        val sizePx = dp(Prefs.bubbleSizeDp(this))
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            setBackgroundResource(R.drawable.bubble_bg)
            val pad = sizePx / 4
            setPadding(pad, pad, pad, pad)
            alpha = Prefs.bubbleAlpha(this@FloatingService) / 100f
        }

        val params = overlayParams(sizePx, sizePx).apply {
            gravity = Gravity.TOP or Gravity.START
            val savedX = Prefs.bubbleX(this@FloatingService)
            val savedY = Prefs.bubbleY(this@FloatingService)
            x = if (savedX >= 0) savedX.coerceIn(0, screenW - sizePx) else screenW - sizePx
            y = if (savedY >= 0) savedY.coerceIn(0, screenH - sizePx) else screenH / 3
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // الزر الأوسط على الأيقونة = رجوع
                    if (isMiddleButton(e)) {
                        onMiddleClick()
                        return@setOnTouchListener true
                    }
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = (startX + dx).toInt().coerceIn(0, screenW - sizePx)
                        params.y = (startY + dy).toInt().coerceIn(0, screenH - sizePx)
                        safeUpdate(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        // الالتصاق بأقرب حافة جانبية حتى لا تحجب المشاهدة
                        params.x = if (params.x + sizePx / 2 < screenW / 2) 0 else screenW - sizePx
                        safeUpdate(view, params)
                        Prefs.setBubblePosition(this, params.x, params.y)
                    } else {
                        expand()
                    }
                    true
                }
                else -> false
            }
        }
        view.setOnGenericMotionListener { _, e -> handleMiddleGeneric(e) }

        try {
            windowManager.addView(view, params)
            bubble = view
            bubbleParams = params
        } catch (e: Exception) {
            Log.e(TAG, "addView(bubble) failed", e)
        }
    }

    private fun removeBubble() {
        bubble?.let { safeRemove(it) }
        bubble = null
        bubbleParams = null
    }

    /** تُستدعى من الإعدادات عند تغيير الحجم أو الشفافية */
    fun applyAppearance() {
        if (panel == null) showBubble()
    }

    // ───────────────────────── القائمة الموسّعة ─────────────────────────

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun expand() {
        removeBubble()
        removePanel()

        val root = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        val grid = root.findViewById<GridLayout>(R.id.grid)
        val visible = FloatAction.values().filter { Prefs.isActionVisible(this, it) }
        grid.columnCount = if (visible.size <= 4) visible.size.coerceAtLeast(1) else 4

        for (action in visible) {
            val item = LayoutInflater.from(this).inflate(R.layout.floating_item, grid, false)
            item.findViewById<ImageView>(R.id.icon).setImageResource(action.iconRes)
            item.findViewById<TextView>(R.id.label).setText(action.labelRes)
            item.setOnClickListener { onActionClicked(action) }
            item.setOnGenericMotionListener { _, e -> handleMiddleGeneric(e) }
            grid.addView(item)
        }
        root.findViewById<TextView>(R.id.emptyText).visibility =
            if (visible.isEmpty()) View.VISIBLE else View.GONE

        // النقر خارج القائمة ⇒ العودة للأيقونة
        root.setOnTouchListener { _, e ->
            when {
                e.actionMasked == MotionEvent.ACTION_OUTSIDE -> {
                    collapse(); true
                }
                e.actionMasked == MotionEvent.ACTION_DOWN && isMiddleButton(e) -> {
                    onMiddleClick(); true
                }
                else -> false
            }
        }
        root.setOnGenericMotionListener { _, e -> handleMiddleGeneric(e) }

        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.25f
            // تأثير زجاجي حقيقي (ضبابية خلف القائمة) إن كان الجهاز يدعمه
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                windowManager.isCrossWindowBlurEnabled
            ) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(24)
            }
        }

        try {
            windowManager.addView(root, params)
            panel = root
        } catch (e: Exception) {
            Log.e(TAG, "addView(panel) failed", e)
            showBubble()
        }
    }

    private fun collapse() {
        removePanel()
        showBubble()
    }

    private fun removePanel() {
        panel?.let { safeRemove(it) }
        panel = null
    }

    private fun onActionClicked(action: FloatAction) {
        if (action.closesPanel) {
            // نغلق القائمة أولاً ثم ننفّذ الأمر على التطبيق الموجود خلفها
            collapse()
            handler.postDelayed({ ActionRunner.run(this, action) }, 120)
        } else {
            ActionRunner.run(this, action)
        }
    }

    // ───────────────────────── الزر الأوسط للماوس ─────────────────────────

    private fun isMiddleButton(e: MotionEvent): Boolean =
        e.buttonState and MotionEvent.BUTTON_TERTIARY != 0

    private fun handleMiddleGeneric(e: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
            e.actionButton == MotionEvent.BUTTON_TERTIARY
        ) {
            onMiddleClick()
            return true
        }
        return false
    }

    private fun onMiddleClick() {
        if (!Prefs.isMiddleBackEnabled(this)) return
        if (panel != null) collapse()
        handler.postDelayed({ ActionRunner.run(this, FloatAction.BACK) }, 80)
    }

    // ───────────────────────── الحارس: استمرار خدمة إمكانية الوصول ─────────────────────────

    private val watchdog = object : Runnable {
        override fun run() {
            AccessibilityGuard.repairAsync(this@FloatingService)
            // إن اختفت الأيقونة لأي سبب نعيدها
            if (Prefs.isFloatingEnabled(this@FloatingService) && bubble == null && panel == null &&
                canDrawOverlays(this@FloatingService)
            ) {
                showBubble()
            }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    // ───────────────────────── الإشعار الدائم ─────────────────────────

    private fun startForegroundSafely() {
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            immutable()
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            immutable()
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW // صامت: بدون صوت ولا اهتزاز ولا منبثق
                    ).apply {
                        setShowBadge(false)
                        setSound(null, null)
                        enableVibration(false)
                        enableLights(false)
                    }
                )
            }
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW).setSound(null)
        }

        @Suppress("DEPRECATION")
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    // ───────────────────────── أدوات ─────────────────────────

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun safeUpdate(v: View, p: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(v, p)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout failed", e)
        }
    }

    private fun safeRemove(v: View) {
        try {
            windowManager.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
    }

    private fun scheduleRestart() {
        try {
            val intent = Intent(applicationContext, FloatingService::class.java)
            val flags = PendingIntent.FLAG_ONE_SHOT or immutable()
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                PendingIntent.getForegroundService(applicationContext, 2, intent, flags)
            else PendingIntent.getService(applicationContext, 2, intent, flags)
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1500, pi)
        } catch (e: Exception) {
            Log.w(TAG, "scheduleRestart failed", e)
        }
    }

    private fun immutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FloatingService"
        private const val CHANNEL_ID = "floating_menu"
        private const val NOTIFICATION_ID = 2001
        private const val WATCHDOG_MS = 30_000L
        const val ACTION_STOP = "com.tvremote.floating.STOP"

        @Volatile
        var instance: FloatingService? = null
            private set

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun start(context: Context) {
            Prefs.setFloatingEnabled(context, true)
            val intent = Intent(context, FloatingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "start failed", e)
            }
        }

        fun stop(context: Context) {
            Prefs.setFloatingEnabled(context, false)
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}
