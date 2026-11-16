package com.tvremote.keyboard

import android.accessibilityservice.AccessibilityService
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
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * خدمة إمكانية الوصول التي تلتقط ضغطات الكيبورد في كل التطبيقات وتنفّذ
 * الوظيفة التي خصّصها المستخدم لكل زر (المحفوظة في KeyMappingStore).
 *
 * الحماية من الإغلاق (ثلاث طبقات):
 *  1) خدمة أمامية حقيقية: الخدمة "مُشغَّلة" (startService) وليست مربوطة فقط،
 *     مع startForeground وإشعار صامت دائم ⇒ أولوية عالية جداً ضد قاتل الذاكرة.
 *  2) START_STICKY: لأن الخدمة مُشغَّلة فعلاً، يعيد النظام تشغيلها إن أُغلقت.
 *  3) ServiceGuard: منبّه كل دقيقة يعيد تفعيل الخدمة في إعدادات إمكانية الوصول
 *     إذا أطفأها النظام (يحتاج صلاحية WRITE_SECURE_SETTINGS مرة واحدة عبر ADB).
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
        isRunning = true

        // أولاً: نصبح خدمة أمامية فوراً (ونحن مربوطون من النظام بأولوية عالية)
        startForegroundSafely()

        // ثانياً: نجعل الخدمة "مُشغَّلة" وليست مربوطة فقط، حتى يعمل START_STICKY.
        // نستخدم startService وليس startForegroundService لأننا أصبحنا أمامية بالفعل،
        // وهكذا لا يوجد شرط "استدعِ startForeground خلال 5 ثوانٍ" قد يسبب انهياراً.
        try {
            startService(Intent(this, KeyboardRemoteService::class.java).setAction(ACTION_STICKY))
        } catch (e: Exception) {
            Log.w(TAG, "startService(self) failed", e)
        }

        // ثالثاً: نشغّل الحارس الدوري
        ServiceGuard.schedulePeriodic(this)
        Log.i(TAG, "Keyboard remote service connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent == null يعني أن النظام أعاد تشغيلنا بعد إغلاق (بفضل START_STICKY)
        startForegroundSafely()

        if (!connected && !isEnabled(this)) {
            // الخدمة غير مفعّلة في الإعدادات: نحاول إعادة تفعيلها إن كان مسموحاً
            ServiceGuard.checkSoon(this)
            if (!ServiceGuard.canRepair(this)) {
                stopForegroundCompat()
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // عند إغلاق التطبيق من قائمة التطبيقات الأخيرة: فحص سريع بعد ثانيتين
        ServiceGuard.checkSoon(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        isRunning = false
        // لا نوقف الإشعار هنا: إن كان الفصل بسبب إغلاق غير مقصود سيعيد الحارس الربط
        ServiceGuard.checkSoon(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        isRunning = false
        stopForegroundCompat()
        ServiceGuard.checkSoon(this)
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
        try {
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
        } catch (e: Exception) {
            // أي انهيار في خدمة إمكانية الوصول يجعل أندرويد يوقفها، لذلك لا نسمح بأي استثناء
            Log.e(TAG, "perform($action) failed", e)
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
            try {
                @Suppress("DEPRECATION")
                node?.recycle()
            } catch (ignored: Exception) {
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                // IMPORTANCE_LOW = إشعار صامت (بدون صوت ولا اهتزاز ولا نافذة منبثقة)
                // لكنه "مرئي" للنظام، فتعتبر الخدمة أمامية حقيقية ولا يتجاهلها
                val channel = NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
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
                .setPriority(Notification.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "KeyboardRemote"
        private const val CHANNEL_ID = "keep_alive_v2"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STICKY = "com.tvremote.keyboard.STICKY"

        private val TEXT_EDITING_KEYS = setOf(
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER
        )

        private var mutedLegacy = false

        /** هل الخدمة متصلة الآن داخل هذه العملية؟ */
        @Volatile
        var isRunning = false
            private set

        /** تضبطه الواجهة أثناء التقاط زر جديد حتى لا تبتلع الخدمة الزر */
        @Volatile
        var captureMode = false

        fun component(context: Context) =
            ComponentName(context, KeyboardRemoteService::class.java)

        /** هل الخدمة مفعّلة في إعدادات إمكانية الوصول؟ */
        fun isEnabled(context: Context): Boolean {
            val expected = component(context)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
