package com.livetranslate.overlay.service

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.livetranslate.overlay.OverlayTrampolineActivity
import com.livetranslate.overlay.capture.Box
import com.livetranslate.overlay.capture.LiveTranslateController
import com.livetranslate.overlay.capture.ScreenCapturer
import com.livetranslate.overlay.data.Prefs
import com.livetranslate.overlay.overlay.ControlPill
import com.livetranslate.overlay.overlay.DraggableFrame
import com.livetranslate.overlay.overlay.EngineStatus
import com.livetranslate.overlay.overlay.OverlayActions
import com.livetranslate.overlay.overlay.OverlayMode
import com.livetranslate.overlay.overlay.OverlayUiState
import com.livetranslate.overlay.overlay.ScreenMetrics
import com.livetranslate.overlay.overlay.StageView
import com.livetranslate.overlay.translate.Languages
import com.livetranslate.overlay.translate.TranslationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * خدمة أمامية (Foreground Service) تملك نافذتين فوق كل التطبيقات:
 *
 * 1. [StageView]: طبقة شفافة بملء الشاشة، FLAG_NOT_TOUCHABLE — للإطار المضيء والنص المترجم.
 * 2. فقاعة التحكم: نافذة WRAP_CONTENT شفافة قابلة للسحب، FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL
 *    حتى تبقى بقية الشاشة قابلة للمس، و FLAG_WATCH_OUTSIDE_TOUCH لاكتشاف بدء التمرير.
 *
 * نوع الخدمة: specialUse دائماً (النافذة العائمة)، ويُضاف mediaProjection أثناء الترجمة فقط.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, OverlayActions {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var prefs: Prefs
    private lateinit var windowContext: Context
    private lateinit var wm: WindowManager

    private var stage: StageView? = null
    private var stageParams: WindowManager.LayoutParams? = null
    private var pillRoot: DraggableFrame? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var pillCenterX = 0f
    private var pillBottom = 0f

    private val uiState = mutableStateOf(OverlayUiState(source = Languages.AUTO, target = "ar"))
    private var engine: TranslationEngine? = null
    private var capturer: ScreenCapturer? = null
    private var controller: LiveTranslateController? = null
    private var languagesChangedWhilePaused = false

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        prefs = Prefs(this)
        windowContext = createOverlayContext()
        wm = windowContext.getSystemService(WindowManager::class.java)
        uiState.value = OverlayUiState(source = prefs.source, target = prefs.target)
        NotificationHelper.createChannel(this)
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val granted = intent?.action == ACTION_PROJECTION_GRANTED &&
            intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) == Activity.RESULT_OK
        // يجب استدعاء startForeground خلال ثوانٍ من startForegroundService في كل الحالات،
        // ومع موافقة التقاط الشاشة يجب أن يتضمن النوع mediaProjection قبل getMediaProjection (Android 14+)
        goForeground(withProjection = capturer != null || granted)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showWindows()

        when (intent?.action) {
            ACTION_OPEN_PICKER -> openPicker()
            ACTION_PROJECTION_GRANTED -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (code == Activity.RESULT_OK && data != null) startProjection(code, data)
                else onCaptureDenied()
            }
            ACTION_PROJECTION_DENIED -> onCaptureDenied()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> Unit // ACTION_SHOW أو إعادة تشغيل من النظام: الفقاعة الحالية تبقى كما هي
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- الخدمة الأمامية

    private fun goForeground(withProjection: Boolean) {
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && withProjection ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else -> 0
        }
        ServiceCompat.startForeground(
            this, NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, withProjection), type,
        )
    }

    // ---------------------------------------------------------------- النوافذ

    /** سياق نافذة مخصص للطبقات العائمة (الطريقة الموصى بها من Android 11). */
    private fun createOverlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private fun baseParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT, // خلفية شفافة تماماً بلا أي إطار
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    private fun showWindows() {
        if (stage == null) addStage()
        if (pillRoot == null) addPill()
    }

    private fun addStage() {
        val m = ScreenMetrics.of(windowContext)
        val params = baseParams(
            m.width, m.height,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        ).apply {
            title = "LiveTranslateStage"
            // Android 12+: أقصى شفافية تسمح بمرور اللمس عبر نافذة غير موثوقة (0.8 افتراضياً)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = windowContext.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
            }
        }
        val view = StageView(windowContext).apply { glowAnimated = prefs.glowAnimated }
        wm.addView(view, params)
        stage = view
        stageParams = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addPill() {
        val m = ScreenMetrics.of(windowContext)
        val density = resources.displayMetrics.density
        pillCenterX = prefs.pillCenterX.takeIf { it >= 0 }?.toFloat() ?: (m.width / 2f)
        pillBottom = prefs.pillBottom.takeIf { it >= 0 }?.toFloat() ?: (m.height - 96 * density)

        val root = DraggableFrame(
            windowContext,
            onDrag = { dx, dy ->
                pillCenterX += dx
                pillBottom += dy
                positionPill()
            },
            onDragEnd = {
                prefs.pillCenterX = pillCenterX.roundToInt()
                prefs.pillBottom = pillBottom.roundToInt()
                controller?.invalidate()
            },
            onOutsideTouch = { controller?.onOutsideTouch() },
        )
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)

        val compose = ComposeView(windowContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { ControlPill(uiState.value, this@OverlayService) }
        }
        root.addView(compose, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        // عند تغيّر حجم الفقاعة (تحوّلها لبطاقة) تبقى متمركزة وتكبر للأعلى
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionPill() }

        val params = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        ).apply { title = "LiveTranslatePill" }
        pillParams = params
        pillRoot = root
        wm.addView(root, params)
        positionPill()
    }

    private fun positionPill() {
        val root = pillRoot ?: return
        val params = pillParams ?: return
        val m = ScreenMetrics.of(windowContext)
        val w = root.width
        val h = root.height
        pillCenterX = clamp(pillCenterX, w / 2f, m.width - w / 2f)
        pillBottom = clamp(pillBottom, h.toFloat(), m.height.toFloat())
        val x = (pillCenterX - w / 2f).roundToInt()
        val y = (pillBottom - h).roundToInt()
        if (x != params.x || y != params.y) {
            params.x = x
            params.y = y
            if (root.isAttachedToWindow) wm.updateViewLayout(root, params)
        }
        capturer?.excludeBox = Box(x, y, x + w, y + h)
    }

    private fun clamp(v: Float, lo: Float, hi: Float) = if (lo > hi) (lo + hi) / 2f else v.coerceIn(lo, hi)

    private fun pillRect(): Rect {
        val p = pillParams ?: return Rect()
        val root = pillRoot ?: return Rect()
        return Rect(p.x, p.y, p.x + root.width, p.y + root.height)
    }

    private fun removeWindows() {
        stage?.let { runCatching { wm.removeView(it) } }
        pillRoot?.let { runCatching { wm.removeView(it) } }
        stage = null
        pillRoot = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val m = ScreenMetrics.of(windowContext)
        stageParams?.let { p ->
            p.width = m.width
            p.height = m.height
            stage?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, p) }
        }
        positionPill()
        capturer?.onDisplayChanged()
        controller?.invalidate()
    }

    // ---------------------------------------------------------------- التقاط الشاشة

    private fun requestCapturePermission() {
        // SYSTEM_ALERT_WINDOW يسمح ببدء نشاط من الخلفية؛ النشاط الشفاف يعرض نافذة موافقة النظام
        startActivity(
            Intent(this, OverlayTrampolineActivity::class.java)
                .setAction(OverlayTrampolineActivity.ACTION_REQUEST_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        releaseProjection()
        val projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        if (projection == null) {
            goForeground(withProjection = false)
            onCaptureDenied()
            return
        }
        val stageView = stage ?: return
        val translationEngine = engine ?: TranslationEngine().also { engine = it }
        lateinit var ctrl: LiveTranslateController
        lateinit var cap: ScreenCapturer
        cap = ScreenCapturer(
            context = windowContext,
            projection = projection,
            onSignature = { ctrl.onSignature(it) },
            // المستخدم أوقف المشاركة من شريط الحالة/النظام
            onProjectionStopped = { if (capturer === cap) stopTranslating() },
        )
        ctrl = LiveTranslateController(
            scope = lifecycleScope,
            capturer = cap,
            engine = translationEngine,
            stage = stageView,
            languages = { uiState.value.source to uiState.value.target },
            excludedRects = { listOf(pillRect()) },
            onStatus = { status -> uiState.value = uiState.value.copy(status = status) },
        )
        capturer = cap
        controller = ctrl
        cap.start()
        positionPill()
        setMode(OverlayMode.TRANSLATING)
        ctrl.start()
    }

    private fun releaseProjection() {
        controller?.stop()
        controller = null
        capturer?.release()
        capturer = null
    }

    private fun onCaptureDenied() {
        uiState.value = uiState.value.copy(status = EngineStatus.CaptureDenied)
        setMode(OverlayMode.PICKER)
    }

    private fun setMode(mode: OverlayMode) {
        uiState.value = uiState.value.copy(mode = mode)
        stage?.setGlowVisible(mode != OverlayMode.LAUNCHER)
    }

    // ---------------------------------------------------------------- أحداث الفقاعة

    override fun openPicker() {
        controller?.pause()
        languagesChangedWhilePaused = false
        // قد يكون المستخدم غيّر الإعدادات من شاشة التطبيق
        stage?.glowAnimated = prefs.glowAnimated
        uiState.value = uiState.value.copy(
            source = prefs.source,
            target = prefs.target,
            status = if (uiState.value.status is EngineStatus.Error) EngineStatus.Ready else uiState.value.status,
        )
        setMode(OverlayMode.PICKER)
    }

    override fun closeOverlay() {
        stopSelf()
    }

    override fun startTranslating() {
        uiState.value = uiState.value.copy(status = EngineStatus.Ready)
        val ctrl = controller
        if (ctrl != null) {
            setMode(OverlayMode.TRANSLATING)
            ctrl.resume(languagesChangedWhilePaused)
            languagesChangedWhilePaused = false
        } else {
            requestCapturePermission()
        }
    }

    override fun stopTranslating() {
        val wasTranslating = capturer != null
        releaseProjection()
        if (wasTranslating) goForeground(withProjection = false)
        uiState.value = uiState.value.copy(status = EngineStatus.Ready)
        setMode(OverlayMode.LAUNCHER)
    }

    override fun selectSource(code: String) {
        prefs.source = code
        uiState.value = uiState.value.copy(source = code)
        languagesChangedWhilePaused = true
    }

    override fun selectTarget(code: String) {
        prefs.target = code
        uiState.value = uiState.value.copy(target = code)
        languagesChangedWhilePaused = true
    }

    override fun swapLanguages() {
        val s = uiState.value
        // لا يمكن عكس "تلقائي"، ويجب أن تكون اللغة المصدر الجديدة مدعومة في القراءة
        if (s.source == Languages.AUTO || Languages.sources.none { it.code == s.target }) return
        if (Languages.targets.none { it.code == s.source }) return
        selectSource(s.target)
        selectTarget(s.source)
    }

    override fun onDestroy() {
        releaseProjection()
        removeWindows()
        engine?.close()
        engine = null
        _running.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.livetranslate.overlay.SHOW"
        const val ACTION_OPEN_PICKER = "com.livetranslate.overlay.OPEN_PICKER"
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
