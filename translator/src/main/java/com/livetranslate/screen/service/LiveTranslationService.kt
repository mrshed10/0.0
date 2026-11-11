package com.livetranslate.screen.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.livetranslate.screen.R
import com.livetranslate.screen.capture.OcrEngine
import com.livetranslate.screen.capture.ScreenCaptureEngine
import com.livetranslate.screen.capture.ScreenMetrics
import com.livetranslate.screen.capture.ScreenSize
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.ContentMode
import com.livetranslate.screen.core.DisplayMode
import com.livetranslate.screen.overlay.OverlayController
import com.livetranslate.screen.settings.LiveSettings
import com.livetranslate.screen.settings.LiveState
import com.livetranslate.screen.settings.ServiceStatus
import com.livetranslate.screen.translate.TranslationEngine
import com.livetranslate.screen.ui.MainActivity
import com.livetranslate.screen.ui.ProjectionPermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * خدمة العمل في الواجهة (Foreground Service): تبقي المحرك والنوافذ العائمة حيّة
 * طوال الوقت مع إشعار دائم للتحكم، ولا تتوقف إلا بطلب صريح من المستخدم.
 */
class LiveTranslationService : Service(), OverlayController.Callbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: LiveSettings
    private lateinit var engine: TranslationEngine
    private lateinit var ocr: OcrEngine
    private lateinit var overlay: OverlayController
    private lateinit var pipeline: LivePipeline

    private var capture: ScreenCaptureEngine? = null
    private var screen: ScreenSize? = null
    private var started = false
    private var projectionRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = LiveSettings.get(this)
        createChannel()
        engine = TranslationEngine(
            scope,
            onModelReady = { scope.launch { pipeline.refresh(clear = false) } },
            onDownloadingChanged = { packs -> scope.launch { LiveState.update { it.copy(downloadingPacks = packs) }; updateNotification() } },
        )
        ocr = OcrEngine()
        overlay = OverlayController(this)
        pipeline = LivePipeline(
            scope, settings, engine, ocr, overlay,
            screenProvider = { currentScreen().bounds },
            captureProvider = { capture },
            requestProjection = ::requestProjection,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // يجب استدعاء startForeground فوراً مع كل بدء
        promoteToForeground(withProjection = capture != null)
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_TOGGLE_PAUSE -> if (started) onTogglePause() else { shutdown(); return START_NOT_STICKY }
            ACTION_TOGGLE_DISPLAY -> if (started) onToggleDisplay() else { shutdown(); return START_NOT_STICKY }
            ACTION_PROJECTION_RESULT -> { handleProjectionResult(intent); startLive() }
            ACTION_PROJECTION_DENIED -> {
                if (!started) { shutdown(); return START_NOT_STICKY }
                projectionRequested = false
                if (settings.config.value.contentMode.usesOcr) {
                    settings.update { it.copy(contentMode = ContentMode.TEXT) }
                    LiveState.update { it.copy(message = "رُفض التقاط الشاشة — تم التحويل إلى وضع النص") }
                }
            }
            else -> { // ACTION_START أو إعادة تشغيل النظام للخدمة (intent == null)
                handleProjectionResult(intent)
                startLive()
            }
        }
        return START_STICKY
    }

    private fun startLive() {
        if (started) return
        if (!Settings.canDrawOverlays(this)) {
            LiveState.update { it.copy(message = "يلزم إذن الظهور فوق التطبيقات") }
            shutdown(); return
        }
        started = true
        val s = ScreenMetrics.real(this)
        screen = s
        val cfg = settings.config.value
        val bubble = ensureBubble(cfg.bubble, s)
        attachOverlay(bubble)
        LiveState.update { ServiceStatus(running = true, projectionActive = capture != null) }
        pipeline.start()

        // إعادة إنشاء النوافذ عند اتصال/انقطاع خدمة إمكانية الوصول (نوافذها موثوقة وأعلى)
        scope.launch {
            ScreenTextAccessibilityService.connected.drop(1).collect {
                if (started) { attachOverlay(settings.config.value.bubble); pipeline.refresh(clear = true) }
            }
        }
        scope.launch {
            settings.config.collect { c ->
                if (!started) return@collect
                overlay.updateControls(c, pipeline.paused)
                if (c.contentMode.usesOcr && capture == null) requestProjection()
            }
        }
        if (cfg.needsProjection && capture == null) requestProjection()
        updateNotification()
    }

    private fun attachOverlay(bubble: Box) {
        val a11y = ScreenTextAccessibilityService.instance
        val cfg = settings.config.value
        overlay.attach(a11y ?: this, a11y != null, currentScreen().bounds, cfg.displayMode, bubble)
        overlay.updateControls(cfg, pipeline.paused)
    }

    private fun currentScreen(): ScreenSize = screen ?: ScreenMetrics.real(this).also { screen = it }

    private fun ensureBubble(b: Box, s: ScreenSize): Box {
        val result = if (b.isEmpty) {
            val w = (s.width * 0.84f).toInt()
            val h = (s.height * 0.26f).toInt()
            val l = (s.width - w) / 2
            val t = (s.height * 0.38f).toInt()
            Box(l, t, l + w, t + h)
        } else b.clampInto(s.bounds, (s.width * 0.25f).toInt(), (s.height * 0.08f).toInt())
        if (result != b) settings.update { it.copy(bubble = result) }
        return result
    }

    // ———————— التقاط الشاشة (MediaProjection) ————————

    private fun requestProjection() {
        if (capture != null || projectionRequested) return
        projectionRequested = true
        val i = Intent(this, ProjectionPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }.onFailure { projectionRequested = false }
    }

    private fun handleProjectionResult(intent: Intent?) {
        intent ?: return
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java) ?: return
        projectionRequested = false
        if (code != Activity.RESULT_OK) return
        // Android 14+: نوع mediaProjection يجب أن يُعلن بعد الموافقة وقبل getMediaProjection
        promoteToForeground(withProjection = true)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching { mpm.getMediaProjection(code, data) }.getOrNull() ?: return
        capture?.release()
        capture = runCatching {
            ScreenCaptureEngine(projection, currentScreen(), onStopped = { scope.launch { onProjectionStopped() } })
        }.getOrNull()
        LiveState.update { it.copy(projectionActive = capture != null, message = null) }
        if (started) pipeline.refresh(clear = true)
    }

    private fun onProjectionStopped() {
        capture?.release()
        capture = null
        LiveState.update { it.copy(projectionActive = false) }
        promoteToForeground(withProjection = false)
        if (settings.config.value.contentMode.usesOcr) {
            LiveState.update { it.copy(message = "توقف التقاط الشاشة — اختر وضع الفيديو/المانغا مجدداً لإعادة السماح") }
            settings.update { it.copy(contentMode = ContentMode.TEXT) }
        }
    }

    // ———————— أوامر الشريط العائم والإشعار ————————

    override fun onBubbleChanged(box: Box, finished: Boolean) {
        settings.update { it.copy(bubble = box) }
        pipeline.onBubbleChanged(finished)
    }

    override fun onContentModeSelected(mode: ContentMode) {
        settings.update { it.copy(contentMode = mode) }
        if (mode.usesOcr && capture == null) requestProjection()
    }

    override fun onTogglePause() {
        pipeline.setPaused(!pipeline.paused)
        LiveState.update { it.copy(paused = pipeline.paused) }
        overlay.updateControls(settings.config.value, pipeline.paused)
        updateNotification()
    }

    override fun onToggleDisplay() {
        val next = if (settings.config.value.displayMode == DisplayMode.FULL_SCREEN) DisplayMode.BUBBLE else DisplayMode.FULL_SCREEN
        settings.update { it.copy(displayMode = next) }
        overlay.applyDisplayMode(next, ensureBubble(settings.config.value.bubble, currentScreen()))
    }

    override fun onSwapDirection() {
        settings.update { c ->
            if (c.sourceLang == "auto") c.copy(targetLang = c.secondaryLang, secondaryLang = c.targetLang)
            else c.copy(sourceLang = c.targetLang, targetLang = c.sourceLang)
        }
    }

    override fun onClose() = shutdown()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!started) return
        val s = ScreenMetrics.real(this)
        if (s == screen) return
        screen = s
        capture?.resize(s)
        val bubble = ensureBubble(settings.config.value.bubble, s)
        overlay.onScreenChanged(s.bounds, bubble)
        pipeline.refresh(clear = true)
    }

    private fun shutdown() {
        started = false
        pipeline.stop()
        overlay.detach()
        capture?.release(); capture = null
        LiveState.update { ServiceStatus(running = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pipeline.stop()
        overlay.detach()
        capture?.release(); capture = null
        engine.close()
        ocr.close()
        scope.cancel()
        LiveState.update { ServiceStatus(running = false) }
        super.onDestroy()
    }

    // ———————— الإشعار الدائم ————————

    private fun promoteToForeground(withProjection: Boolean) {
        val type = when {
            Build.VERSION.SDK_INT >= 34 ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && withProjection ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        if (!started) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val cfg = settings.config.value
        val paused = started && pipeline.paused
        val modeText = when (cfg.contentMode) {
            ContentMode.TEXT -> getString(R.string.mode_text)
            ContentMode.VIDEO -> getString(R.string.mode_video)
            ContentMode.MANGA -> getString(R.string.mode_manga)
        }
        val displayText = if (cfg.displayMode == DisplayMode.BUBBLE) getString(R.string.mode_bubble) else getString(R.string.mode_full)
        val downloading = LiveState.status.value.downloadingPacks
        val text = buildString {
            append(displayText).append(" • ").append(modeText)
            if (downloading.isNotEmpty()) append(" • جارٍ تنزيل حزمة لغة…")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle(getString(if (paused) R.string.notif_paused else R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                getString(if (paused) R.string.action_resume else R.string.action_pause),
                actionIntent(ACTION_TOGGLE_PAUSE, 1),
            )
            .addAction(R.drawable.ic_bubble, getString(R.string.action_switch_display), actionIntent(ACTION_TOGGLE_DISPLAY, 2))
            .addAction(R.drawable.ic_close, getString(R.string.action_stop), actionIntent(ACTION_STOP, 3))
            .build()
    }

    private fun actionIntent(action: String, req: Int): PendingIntent = PendingIntent.getService(
        this, req, Intent(this, LiveTranslationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "live_translation"
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.livetranslate.screen.START"
        const val ACTION_STOP = "com.livetranslate.screen.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.livetranslate.screen.TOGGLE_PAUSE"
        const val ACTION_TOGGLE_DISPLAY = "com.livetranslate.screen.TOGGLE_DISPLAY"
        const val ACTION_PROJECTION_RESULT = "com.livetranslate.screen.PROJECTION_RESULT"
        const val ACTION_PROJECTION_DENIED = "com.livetranslate.screen.PROJECTION_DENIED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int? = null, data: Intent? = null) {
            val i = Intent(context, LiveTranslationService::class.java).setAction(ACTION_START)
            if (resultCode != null && data != null) {
                i.putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun deliverProjection(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, LiveTranslationService::class.java).setAction(ACTION_PROJECTION_RESULT)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, i)
        }

        fun projectionDenied(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, LiveTranslationService::class.java).setAction(ACTION_PROJECTION_DENIED),
            )
        }

        fun stop(context: Context) {
            if (!LiveState.status.value.running) return
            context.startService(Intent(context, LiveTranslationService::class.java).setAction(ACTION_STOP))
        }
    }
}
