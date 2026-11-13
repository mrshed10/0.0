package com.livetranslate.overlay.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.livetranslate.overlay.R
import com.livetranslate.overlay.a11y.TranslateAccessibilityService
import com.livetranslate.overlay.capture.LiveTranslateController
import com.livetranslate.overlay.capture.ScreenCapturer
import com.livetranslate.overlay.data.Prefs
import com.livetranslate.overlay.overlay.EngineStatus
import com.livetranslate.overlay.overlay.ScreenMetrics
import com.livetranslate.overlay.overlay.StageView
import com.livetranslate.overlay.translate.Languages
import com.livetranslate.overlay.translate.ModelStore
import com.livetranslate.overlay.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * خدمة أمامية تعمل فقط أثناء الترجمة. لا تضيف أي فقاعة أو شريط أو أيقونة على الشاشة:
 * نافذتها الوحيدة طبقة [StageView] شفافة بالكامل لا تستقبل اللمس، ولا يظهر فيها إلا النص المترجم.
 *
 * التحكم: زر الإعدادات السريعة، أو إشعار الخدمة (إيقاف)، أو شاشة التطبيق.
 *
 * مضيف الطبقة:
 * - إن كانت خدمة إمكانية الوصول مفعّلة: TYPE_ACCESSIBILITY_OVERLAY بعتامة ‎100%‎ (استبدال كامل للنص).
 * - وإلا: TYPE_APPLICATION_OVERLAY بأقصى عتامة يسمح بها النظام مع مرور اللمس (0.8 على Android 12+).
 */
class OverlayService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var appOverlayContext: Context
    private var languageListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var stage: StageView? = null
    private var stageWm: WindowManager? = null
    private var stageParams: WindowManager.LayoutParams? = null

    private var engine: TranslationEngine? = null
    private var capturer: ScreenCapturer? = null
    private var controller: LiveTranslateController? = null
    private var prepareJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        appOverlayContext = createOverlayContext()
        NotificationHelper.createChannel(this)
        _running.value = true

        // تفعيل/تعطيل خدمة إمكانية الوصول أثناء العمل ينقل الطبقة بين المضيفين
        lifecycleScope.launch {
            TranslateAccessibilityService.connected.drop(1).collect { if (stage != null) attachStage() }
        }
        languageListener = prefs.observeLanguages { onLanguagesChanged() }

        // حزمة لغة اكتملت في الخلفية (لغة اكتُشفت تلقائياً): نعيد ترجمة الشاشة لتظهر فوراً
        lifecycleScope.launch {
            ModelStore.downloaded.drop(1).collect { controller?.invalidate() }
        }
        lifecycleScope.launch {
            ModelStore.downloading.drop(1).collect { code ->
                if (controller == null) return@collect
                updateNotification(
                    if (code != null) getString(R.string.notif_text_downloading, Languages.nameOf(code)) else activeText(),
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val granted = intent?.action == ACTION_PROJECTION_GRANTED &&
            intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) == Activity.RESULT_OK
        // startForeground مطلوب خلال ثوانٍ في كل الحالات؛ ومع موافقة الالتقاط يجب أن يتضمن
        // النوع mediaProjection قبل getMediaProjection (Android 14+)
        goForeground(withProjection = capturer != null || granted, text = getString(R.string.notif_text_preparing))

        when (intent?.action) {
            ACTION_PROJECTION_GRANTED -> {
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (granted && data != null) startTranslating(intent.getIntExtra(EXTRA_RESULT_CODE, 0), data)
                else stopSelf()
            }
            ACTION_PROJECTION_DENIED, ACTION_STOP -> stopSelf()
            // إعادة تشغيل من النظام: لا يمكن استعادة موافقة الالتقاط، فنتوقف بهدوء
            else -> if (capturer == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- الخدمة الأمامية والإشعار

    private fun goForeground(withProjection: Boolean, text: String) {
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && withProjection ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else -> 0
        }
        ServiceCompat.startForeground(
            this, NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, text), type,
        )
    }

    private fun updateNotification(text: String) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, text))
        }
    }

    private fun activeText() = getString(
        R.string.notif_text_active, Languages.nameOf(prefs.source), Languages.nameOf(prefs.target),
    )

    // ---------------------------------------------------------------- طبقة الترجمة

    /** سياق نافذة مخصص للطبقات العائمة (الطريقة الموصى بها من Android 11). */
    private fun createOverlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    /** يضيف الطبقة (أو ينقلها) إلى أفضل مضيف متاح. يُرجع false إن لم يتوفر أي إذن. */
    private fun attachStage(): Boolean {
        detachStage()
        val a11y = TranslateAccessibilityService.instance
        val trusted = a11y != null
        if (!trusted && !Settings.canDrawOverlays(this)) return false
        val host: Context = a11y ?: appOverlayContext
        val type = if (trusted) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val m = ScreenMetrics.of(host)
        val params = WindowManager.LayoutParams(
            m.width, m.height, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT, // شفافة تماماً: لا خلفية ولا إطار ولا ظل
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
            title = "LiveTranslateStage"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
            // النافذة غير الموثوقة: أقصى عتامة تسمح بمرور اللمس للتطبيق تحتها
            if (!trusted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
            }
        }
        val view = stage ?: StageView(this).also { stage = it }
        val wm = host.getSystemService(WindowManager::class.java)
        return runCatching { wm.addView(view, params) }
            .onSuccess {
                stageWm = wm
                stageParams = params
            }
            .isSuccess
    }

    private fun detachStage() {
        val view = stage ?: return
        stageWm?.let { wm -> runCatching { wm.removeViewImmediate(view) } }
        stageWm = null
        stageParams = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val wm = stageWm
        val p = stageParams
        val view = stage
        if (wm != null && p != null && view != null) {
            val m = ScreenMetrics.of(appOverlayContext)
            p.width = m.width
            p.height = m.height
            runCatching { wm.updateViewLayout(view, p) }
        }
        capturer?.onDisplayChanged()
        controller?.invalidate()
    }

    // ---------------------------------------------------------------- الترجمة

    private fun startTranslating(resultCode: Int, data: Intent) {
        releaseProjection()
        if (stage == null || stageWm == null) {
            if (!attachStage()) {
                stopSelf()
                return
            }
        }
        val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        if (projection == null) {
            stopSelf()
            return
        }
        val stageView = stage ?: return
        val translationEngine = engine ?: TranslationEngine().also { engine = it }

        lateinit var ctrl: LiveTranslateController
        lateinit var cap: ScreenCapturer
        cap = ScreenCapturer(
            context = appOverlayContext,
            projection = projection,
            onSignature = { ctrl.onSignature(it) },
            // المستخدم أوقف المشاركة من شريط الحالة/النظام
            onProjectionStopped = { if (capturer === cap) stopSelf() },
        )
        ctrl = LiveTranslateController(
            scope = lifecycleScope,
            capturer = cap,
            engine = translationEngine,
            stage = stageView,
            languages = { prefs.source to prefs.target },
            onStatus = ::onEngineStatus,
        )
        capturer = cap
        controller = ctrl

        // تجهيز الحزم وتسخين النماذج قبل أول إطار، ثم البدء. لا يظهر شيء على الشاشة أثناء ذلك.
        prepareJob?.cancel()
        prepareJob = lifecycleScope.launch {
            try {
                translationEngine.prepare(prefs.source, prefs.target)
                updateNotification(activeText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateNotification(getString(R.string.notif_text_model_error))
            }
            if (capturer === cap) {
                cap.start()
                ctrl.start()
            }
        }
    }

    private fun onEngineStatus(status: EngineStatus) {
        if (status is EngineStatus.Error) {
            updateNotification(getString(R.string.notif_text_error, status.message))
        }
    }

    private fun onLanguagesChanged() {
        val ctrl = controller ?: return
        val translationEngine = engine ?: return
        prepareJob?.cancel()
        prepareJob = lifecycleScope.launch {
            updateNotification(getString(R.string.notif_text_preparing))
            runCatching { translationEngine.prepare(prefs.source, prefs.target) }
                .onSuccess { updateNotification(activeText()) }
                .onFailure { if (it !is CancellationException) updateNotification(getString(R.string.notif_text_model_error)) }
            ctrl.invalidate()
        }
    }

    private fun releaseProjection() {
        prepareJob?.cancel()
        controller?.stop()
        controller = null
        capturer?.release()
        capturer = null
    }

    override fun onDestroy() {
        languageListener?.let { prefs.stopObserving(it) }
        releaseProjection()
        detachStage()
        stage = null
        engine?.close()
        engine = null
        _running.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_PROJECTION_GRANTED = "com.livetranslate.overlay.PROJECTION_GRANTED"
        const val ACTION_PROJECTION_DENIED = "com.livetranslate.overlay.PROJECTION_DENIED"
        const val ACTION_STOP = "com.livetranslate.overlay.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun intent(context: Context, action: String) = Intent(context, OverlayService::class.java).setAction(action)
    }
}
