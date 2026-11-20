package com.localcam.surveillance

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * الشاشة الرئيسية:
 *  - عند عدم التسجيل: معاينة محلية مرتبطة بهذه الشاشة فقط.
 *  - أثناء التسجيل: الكاميرا ملك الخدمة، والشاشة ترتبط بها لعرض المعاينة فقط.
 *  - زر الشاشة السوداء: يغطي الشاشة بالأسود ويخفض السطوع ويوقف المعاينة، والعودة بلمستين.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var previewView: PreviewView
    private lateinit var btnRecord: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvWarning: TextView
    private lateinit var blackoutOverlay: View
    private lateinit var tvBlackoutHint: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var localPreview: Preview? = null
    private var recordingService: RecordingService? = null
    private var serviceBound = false

    private var isRecording = false
    private var lastSyncedRecording: Boolean? = null
    private var isBlackout = false
    private var pendingStart = false
    private var lastThermalShown: String? = null

    // ---------------- الربط مع الخدمة ----------------

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            recordingService = (binder as RecordingService.LocalBinder).getService()
            if (!isBlackout) attachServicePreview()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
        }
    }

    // ---------------- الأذونات ----------------

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                pendingStart = false
                Toast.makeText(this, "لا يعمل التطبيق بدون إذن الكاميرا", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (pendingStart) {
                pendingStart = false
                startRecording()
            } else {
                lastSyncedRecording = null
                render(RecordingState.status.value)
            }
        }

    // زر الرجوع أثناء الشاشة السوداء = الخروج منها (بدل إغلاق التطبيق)
    private val blackoutBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitBlackout()
    }

    // ======================= دورة الحياة =======================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)

        previewView = findViewById(R.id.previewView)
        btnRecord = findViewById(R.id.btnRecord)
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvWarning = findViewById(R.id.tvWarning)
        blackoutOverlay = findViewById(R.id.blackoutOverlay)
        tvBlackoutHint = findViewById(R.id.tvBlackoutHint)

        btnRecord.setOnClickListener { if (isRecording) confirmStop() else startRecordingWithPermissions() }
        findViewById<MaterialButton>(R.id.btnClips).setOnClickListener {
            startActivity(Intent(this, ClipsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnBlackout).setOnClickListener { enterBlackout() }
        tvWarning.setOnClickListener { enterBlackout() }

        setupBlackoutGestures()
        onBackPressedDispatcher.addCallback(this, blackoutBackCallback)

        // مراقبة حالة التسجيل + عدّاد الوقت (فقط أثناء ظهور الشاشة)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { RecordingState.status.collect { render(it) } }
                launch {
                    while (isActive) {
                        updateTimer()
                        delay(1_000)
                    }
                }
            }
        }

        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(requiredPermissions())
        }
        if (savedInstanceState == null) BatteryHelper.showTipIfNeeded(this)
    }

    override fun onStop() {
        super.onStop()
        // نوقف المعاينة فقط — التسجيل في الخدمة مستمر
        lastSyncedRecording = null
        disconnectService()
        releaseLocalPreview()
    }

    // ======================= عرض الحالة =======================

    private fun render(status: RecorderStatus) {
        isRecording = status.isRecording
        if (lastSyncedRecording != isRecording) {
            lastSyncedRecording = isRecording
            syncCamera()
        }

        if (isRecording) {
            btnRecord.text = getString(R.string.stop_recording)
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.backgroundTintList = ContextCompat.getColorStateList(this, R.color.record_red)
            tvStatus.text = "● جاري التسجيل — ${status.currentFile ?: "جارٍ التجهيز…"}"
            tvTimer.visibility = View.VISIBLE
        } else {
            btnRecord.text = getString(R.string.start_recording)
            btnRecord.setIconResource(R.drawable.ic_videocam)
            btnRecord.backgroundTintList = ContextCompat.getColorStateList(this, R.color.record_green)
            tvStatus.text = getString(R.string.status_idle)
            tvTimer.visibility = View.GONE
        }

        // تنبيه الحرارة/التخزين: نص بسيط + Toast عند ظهوره أول مرة، دون أي إيقاف للتسجيل
        val warning = status.thermalWarning ?: status.message
        tvWarning.visibility = if (warning != null) View.VISIBLE else View.GONE
        tvWarning.text = warning
        if (status.thermalWarning != null && lastThermalShown == null && !isBlackout) {
            Toast.makeText(this, "تنبيه حرارة: التسجيل مستمر. اضغط التنبيه لتفعيل الشاشة السوداء", Toast.LENGTH_LONG).show()
        }
        lastThermalShown = status.thermalWarning
        updateTimer()
    }

    private fun updateTimer() {
        val s = RecordingState.status.value
        if (!s.isRecording || s.segmentStartedAt == 0L) return
        val now = System.currentTimeMillis()
        val segment = formatDuration(now - s.segmentStartedAt)
        val total = formatDuration(now - s.recordingStartedAt)
        val limit = formatDuration(s.segmentMs)
        tvTimer.text = "المقطع: $segment / $limit   •   الإجمالي: $total   •   مقاطع محفوظة: ${s.segmentsSaved}"
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    // ======================= الكاميرا والمعاينة =======================

    /** يقرر من يملك الكاميرا: الشاشة (معاينة فقط) أو الخدمة (تسجيل). */
    private fun syncCamera() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (!hasPermission(Manifest.permission.CAMERA)) return
        if (isRecording) {
            releaseLocalPreview()
            connectService()
        } else {
            disconnectService()
            startLocalPreview()
        }
    }

    private fun startLocalPreview() {
        if (isBlackout) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isRecording || isBlackout || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@addListener
            }
            val provider = future.get()
            cameraProvider = provider
            if (localPreview != null) return@addListener
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val selector = if (settings.useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.bindToLifecycle(this, selector, preview)
                localPreview = preview
            } catch (e: Exception) {
                try {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    localPreview = preview
                } catch (_: Exception) {
                    Toast.makeText(this, "تعذر تشغيل المعاينة", Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseLocalPreview() {
        localPreview?.let { preview ->
            try { cameraProvider?.unbind(preview) } catch (_: Exception) { }
        }
        localPreview = null
    }

    private fun connectService() {
        if (serviceBound) return
        // العَلَم 0: نرتبط فقط إن كانت الخدمة تعمل، ولا ننشئها
        serviceBound = bindService(Intent(this, RecordingService::class.java), serviceConnection, 0)
    }

    private fun disconnectService() {
        recordingService?.detachPreview()
        recordingService = null
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) { }
            serviceBound = false
        }
    }

    private fun attachServicePreview() {
        recordingService?.attachPreview(previewView.surfaceProvider, currentRotation())
    }

    private fun currentRotation(): Int = previewView.display?.rotation ?: Surface.ROTATION_0

    // ======================= بدء/إيقاف التسجيل =======================

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (settings.recordAudio) list += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    private fun startRecordingWithPermissions() {
        val missing = requiredPermissions().filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            startRecording()
        } else {
            pendingStart = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startRecording() {
        releaseLocalPreview() // نحرر الكاميرا لتأخذها الخدمة
        RecordingService.start(this, currentRotation())
        // منع الضغط المزدوج السريع
        btnRecord.isEnabled = false
        btnRecord.postDelayed({ btnRecord.isEnabled = true }, 1_500)
        if (!BatteryHelper.isUnrestricted(this)) {
            Toast.makeText(this, "نصيحة: اضبط البطارية على «غير مقيدة» من الإعدادات للتسجيل الطويل", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmStop() {
        MaterialAlertDialogBuilder(this)
            .setTitle("إيقاف التسجيل؟")
            .setMessage("سيتم حفظ المقطع الحالي وإيقاف المراقبة.")
            .setPositiveButton("إيقاف") { _, _ ->
                val service = recordingService
                if (service != null) service.requestStop() else RecordingService.stop(this)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ======================= الشاشة السوداء =======================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBlackoutGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                exitBlackout()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                flashBlackoutHint() // لمسة واحدة: نُظهر التلميح لثوانٍ فقط
                return true
            }
        })
        blackoutOverlay.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun enterBlackout() {
        if (isBlackout) return
        isBlackout = true
        blackoutOverlay.visibility = View.VISIBLE
        flashBlackoutHint()

        // أقل سطوع ممكن + إخفاء الأشرطة + إبقاء الشاشة "مفتوحة" (سوداء) لتعمل اللمستان
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // إيقاف المعاينة يوفر المعالج والحرارة، والتسجيل مستمر في الخدمة
        recordingService?.detachPreview()
        releaseLocalPreview()
        blackoutBackCallback.isEnabled = true
    }

    private fun exitBlackout() {
        if (!isBlackout) return
        isBlackout = false
        blackoutOverlay.visibility = View.GONE
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        blackoutBackCallback.isEnabled = false

        if (isRecording) attachServicePreview() else startLocalPreview()
    }

    private fun flashBlackoutHint() {
        tvBlackoutHint.animate().cancel()
        tvBlackoutHint.alpha = 1f
        tvBlackoutHint.animate().alpha(0f).setStartDelay(3_000).setDuration(1_000).start()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
