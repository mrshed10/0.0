package com.tvtools.floatingmenu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.overlay.FloatingBubble
import com.tvtools.floatingmenu.overlay.MenuPanel
import com.tvtools.floatingmenu.system.OverlayPermission

/**
 * خدمة أمامية (Foreground Service) تبقي الزر العائم والقائمة تعمل في الخلفية فوق أي تطبيق.
 * START_STICKY: يعيد النظام تشغيلها إذا أوقفها لنقص الذاكرة.
 */
class OverlayService : Service(), MenuPanel.Listener {

    private lateinit var prefs: Prefs
    private lateinit var windowManager: WindowManager
    private lateinit var themedContext: Context
    private var bubble: FloatingBubble? = null
    private var menu: MenuPanel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = Prefs(this)
        windowManager = getSystemService(WindowManager::class.java)
        themedContext = ContextThemeWrapper(this, R.style.Theme_TvOverlay)
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // يجب استدعاء startForeground بعد كل startForegroundService وإلا يُغلق التطبيق.
        startInForeground()

        if (!OverlayPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                prefs.serviceEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_MENU -> showMenu()
            ACTION_TOGGLE_MENU -> if (menu?.isShowing == true) menu?.close() else showMenu()
            ACTION_REFRESH_BUBBLE -> bubble?.hide() // يُعاد إظهاره بالموضع/الحالة الجديدة أدناه
        }
        syncBubble()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubble?.onScreenChanged()
    }

    override fun onDestroy() {
        menu?.dismiss()
        bubble?.hide()
        menu = null
        bubble = null
        isRunning = false
        super.onDestroy()
    }

    // ---------- الزر والقائمة ----------

    private fun requireBubble(): FloatingBubble =
        bubble ?: FloatingBubble(themedContext, windowManager, prefs) { showMenu() }.also { bubble = it }

    private fun requireMenu(): MenuPanel =
        menu ?: MenuPanel(themedContext, windowManager, prefs, this).also { menu = it }

    /** يظهر الزر إذا كان مفعّلاً والقائمة مغلقة، ويخفيه غير ذلك. */
    private fun syncBubble() {
        val shouldShow = prefs.bubbleVisible && menu?.isShowing != true
        if (shouldShow) {
            if (!requireBubble().show()) stopSelf() // الصلاحية سُحبت
        } else {
            bubble?.hide()
        }
    }

    private fun showMenu() {
        val onLeft = requireBubble().isOnLeftSide()
        bubble?.hide()
        if (!requireMenu().show(onLeft)) syncBubble()
    }

    override fun onMenuClosed() = syncBubble()

    override fun onStopRequested() {
        prefs.serviceEnabled = false
        stopSelf()
    }

    // ---------- الإشعار الدائم ----------

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PENDING_FLAGS,
        )
        return builder
            .setSmallIcon(R.drawable.ic_stat_menu)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_apps),
                    getString(R.string.notif_open_menu),
                    servicePendingIntent(ACTION_SHOW_MENU, 1),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_power),
                    getString(R.string.service_stop),
                    servicePendingIntent(ACTION_STOP, 2),
                ).build(),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, PENDING_FLAGS)
        } else {
            PendingIntent.getService(this, requestCode, intent, PENDING_FLAGS)
        }
    }

    companion object {
        const val ACTION_START = "com.tvtools.floatingmenu.action.START"
        const val ACTION_SHOW_MENU = "com.tvtools.floatingmenu.action.SHOW_MENU"
        const val ACTION_TOGGLE_MENU = "com.tvtools.floatingmenu.action.TOGGLE_MENU"
        const val ACTION_REFRESH_BUBBLE = "com.tvtools.floatingmenu.action.REFRESH_BUBBLE"
        const val ACTION_STOP = "com.tvtools.floatingmenu.action.STOP"

        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 42
        private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, OverlayService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
