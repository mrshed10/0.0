package com.tvremote.keyboard

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * خدمة إمكانية الوصول التي تلتقط ضغطات الكيبورد في كل التطبيقات وتنفّذ
 * الوظيفة التي خصّصها المستخدم لكل زر (المحفوظة في KeyMappingStore).
 *
 * الحماية من الإغلاق:
 *  - تعمل كخدمة أمامية (Foreground Service) بإشعار صامت دائم.
 *  - START_STICKY + إعادة تشغيل تلقائية عبر AlarmManager في onTaskRemoved.
 *  - النظام نفسه يعيد ربط خدمات إمكانية الوصول المفعّلة بعد إعادة تشغيل الجهاز.
 */
class KeyboardRemoteService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private var connected = false

    // ───────────────────────── دورة الحياة ─────────────────────────

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        startForegroundSafely()
        Log.i(TAG, "Keyboard remote service connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // قد نصل هنا من منبّه إعادة التشغيل؛ يجب استدعاء startForeground خلال 5 ثوانٍ
        startForegroundSafely()
        if (!connected && !isEnabled(this)) {
            // المستخدم أوقف الخدمة من الإعدادات: لا داعي للبقاء
            stopForegroundCompat()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // عند إغلاق التطبيق من قائمة التطبيقات الأخيرة: جدولة إعادة تشغيل الخدمة
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        stopForegroundCompat()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        if (isEnabled(this)) scheduleRestart() // أُغلقت رغم أنها مفعّلة: أعدها
        stopForegroundCompat()
        super.onDestroy()
    }

    // ───────────────────────── التقاط الأزرار ─────────────────────────

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // أثناء نافذة "اضغط الزر المطلوب" نترك كل الأزرار تصل للتطبيق
        if (captureMode) return false

        val action = KeyMappingStore.get(this, event.keyCode) ?: return false

        // أثناء الكتابة داخل مربع نص لا نعطّل أزرار الكتابة (Backspace، المسافة، الحروف...)
        if (isTypingKey(event) && isEditingText()) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0 || action.repeatable) perform(action)
        }
        // نستهلك DOWN و UP معاً حتى لا يستقبل التطبيق المفتوح نصف ضغطة
        return true
    }

    private fun perform(action: RemoteAction) {
        when (action) {
            RemoteAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            RemoteAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            RemoteAction.MUTE -> toggleMute()
            RemoteAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            RemoteAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            RemoteAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            RemoteAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            RemoteAction.PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            RemoteAction.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            RemoteAction.MEDIA_PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun isTypingKey(event: KeyEvent): Boolean =
        event.isPrintingKey || event.keyCode in TEXT_EDITING_KEYS

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
            AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI
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
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    // ───────────────────────── الخدمة الأمامية ─────────────────────────

    private fun startForegroundSafely() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // بعض الأنظمة ترفض البدء من الخلفية؛ الخدمة تستمر كخدمة إمكانية وصول عادية
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
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            immutableFlag()
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                // IMPORTANCE_MIN = إشعار صامت تماماً: بدون صوت ولا اهتزاز ولا نافذة منبثقة
                val channel = NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                }
                manager.createNotificationChannel(channel)
            }
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setPriority(Notification.PRIORITY_MIN)
                .setSound(null)
                .setVibrate(null)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    /** إعادة تشغيل الخدمة بعد ثانيتين عبر AlarmManager */
    private fun scheduleRestart() {
        try {
            val intent = Intent(applicationContext, KeyboardRemoteService::class.java)
                .setAction(ACTION_KEEP_ALIVE)
            val flags = PendingIntent.FLAG_ONE_SHOT or immutableFlag()
            val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(applicationContext, 1, intent, flags)
            } else {
                PendingIntent.getService(applicationContext, 1, intent, flags)
            }
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                pending
            )
        } catch (e: Exception) {
            Log.w(TAG, "scheduleRestart failed", e)
        }
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "KeyboardRemote"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_MS = 2000L
        private const val ACTION_KEEP_ALIVE = "com.tvremote.keyboard.KEEP_ALIVE"

        private val TEXT_EDITING_KEYS = setOf(
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER
        )

        private var mutedLegacy = false

        /** تضبطه الواجهة أثناء التقاط زر جديد حتى لا تبتلع الخدمة الزر */
        @Volatile
        var captureMode = false

        /** هل الخدمة مفعّلة في إعدادات إمكانية الوصول؟ */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, KeyboardRemoteService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
