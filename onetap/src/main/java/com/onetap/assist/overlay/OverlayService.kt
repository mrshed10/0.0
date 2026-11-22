package com.onetap.assist.overlay

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.onetap.assist.AssistLauncher
import com.onetap.assist.MainActivity
import com.onetap.assist.Prefs
import com.onetap.assist.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that hosts the floating button. It does no work while idle:
 * no timers, no polling, no wake locks — it only reacts to touches and screen on/off broadcasts.
 */
class OverlayService : Service() {

    enum class State { STOPPED, VISIBLE, HIDDEN }

    private lateinit var prefs: Prefs
    private var controller: FloatingButtonController? = null
    private var userHidden = false
    private var screenOff = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.K_SIZE, Prefs.K_OPACITY, Prefs.K_SNAP -> controller?.applyAppearance()
            Prefs.K_AUTOHIDE -> applyVisibility()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_USER_PRESENT -> screenOff = false
                Intent.ACTION_SCREEN_ON -> {
                    // No secure lock screen → USER_PRESENT may never arrive; show right away.
                    val km = getSystemService(KeyguardManager::class.java)
                    if (!km.isKeyguardLocked) screenOff = false
                }
            }
            applyVisibility()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
        controller = FloatingButtonController(
            service = this,
            prefs = prefs,
            onTap = { AssistLauncher.trigger(this, prefs) },
            onLongPress = {
                userHidden = true
                applyVisibility()
                Toast.makeText(this, R.string.toast_hidden, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { stopSelf() },
        )
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService(), even if we stop right away.
        goForeground()

        if (!Settings.canDrawOverlays(this) || intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_HIDE -> userHidden = true
            ACTION_SHOW -> userHidden = false
            ACTION_TOGGLE -> userHidden = !userHidden
            else -> userHidden = false // ACTION_START or a sticky restart
        }
        applyVisibility()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onScreenChanged()
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        runCatching { unregisterReceiver(screenReceiver) }
        controller?.destroy()
        controller = null
        _state.value = State.STOPPED
        super.onDestroy()
    }

    private fun applyVisibility() {
        val c = controller ?: return
        val show = !userHidden && !(screenOff && prefs.autoHideScreenOff)
        if (show) c.show() else c.hide()
        val newState = if (userHidden) State.HIDDEN else State.VISIBLE
        if (_state.value != newState) {
            _state.value = newState
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
    }

    // ---- notification ------------------------------------------------------------------------

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN)
            .apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(): Notification {
        val hidden = userHidden
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (hidden) R.string.notif_title_hidden else R.string.notif_title_visible))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(if (hidden) R.string.action_show else R.string.action_hide),
                servicePending(if (hidden) ACTION_SHOW else ACTION_HIDE, 1),
            )
            .addAction(0, getString(R.string.action_stop), servicePending(ACTION_STOP, 2))
            .build()
    }

    companion object {
        const val ACTION_START = "com.onetap.assist.action.START"
        const val ACTION_STOP = "com.onetap.assist.action.STOP"
        const val ACTION_SHOW = "com.onetap.assist.action.SHOW"
        const val ACTION_HIDE = "com.onetap.assist.action.HIDE"
        const val ACTION_TOGGLE = "com.onetap.assist.action.TOGGLE"
        private const val CHANNEL_ID = "floating_button"
        private const val NOTIF_ID = 1

        private val _state = MutableStateFlow(State.STOPPED)
        val state: StateFlow<State> = _state.asStateFlow()

        fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, OverlayService::class.java).setAction(action),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
