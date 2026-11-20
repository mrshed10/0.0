package com.localcam.surveillance

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File

/**
 * قلب التطبيق: خدمة أمامية (Foreground Service) تمتلك الكاميرا وتسجّل بشكل متواصل.
 *
 *  - التسجيل المقسّم: كل مقطع له حد زمني (5 دقائق افتراضياً). عند انتهائه يصل حدث Finalize
 *    فنبدأ المقطع التالي فوراً في نفس اللحظة (الفاصل جزء من الثانية).
 *  - قبل كل مقطع: فحص المساحة وحذف أقدم مقطع غير مقفل عند الحاجة.
 *  - WakeLock جزئي يبقي المعالج يعمل حتى مع إطفاء الشاشة.
 *  - مراقبة الحرارة: تنبيه فقط، دون إيقاف التسجيل.
 *  - أي خطأ (الكاميرا أخذها تطبيق آخر، امتلاء...) => إعادة محاولة تلقائية بدل التوقف.
 *
 *  الشاشة الرئيسية ترتبط بالخدمة (bind) لتعرض المعاينة فقط، والتسجيل لا يعتمد عليها.
 */
class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "RecordingService"

        const val ACTION_START = "com.localcam.surveillance.action.START"
        const val ACTION_STOP = "com.localcam.surveillance.action.STOP"
        const val EXTRA_ROTATION = "rotation"

        private const val CHANNEL_RECORDING = "recording"
        private const val CHANNEL_ALERTS = "alerts"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002

        private const val RETRY_DELAY_MS = 3_000L

        fun start(context: Context, rotation: Int) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROTATION, rotation)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }

    /** يسمح للشاشة الرئيسية بالوصول للخدمة لعرض المعاينة. */
    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var thermalMonitor: ThermalMonitor
    private lateinit var notificationManager: NotificationManager

    // الكاميرا
    private val preview: Preview = Preview.Builder().build()
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentFile: File? = null
    private var targetRotation = Surface.ROTATION_0

    // الحالة
    private var started = false
    private var stopRequested = false
    private var finished = false
    private var audioEnabled = false
    private var segmentMs = 5 * 60_000L
    private var lastThermalLevel = 0
    private var rebindOnRetry = false

    /** احتياط: إن لم يُنهِ الحد الزمني المقطع لأي سبب، نوقفه يدوياً فيبدأ التالي. */
    private val segmentWatchdog = Runnable {
        Log.w(TAG, "المؤقت الاحتياطي أنهى المقطع")
        activeRecording?.stop()
    }

    private val retryRunnable = Runnable { retry() }

    // ======================= دورة حياة الخدمة =======================

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannels()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalCam::Recording")
            .apply { setReferenceCounted(false) }

        thermalMonitor = ThermalMonitor(this) { level, message -> onThermalChanged(level, message) }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }

        if (!started) {
            targetRotation = intent?.getIntExtra(EXTRA_ROTATION, Surface.ROTATION_0) ?: Surface.ROTATION_0
            audioEnabled = settings.recordAudio && hasPermission(Manifest.permission.RECORD_AUDIO)

            // يجب استدعاء startForeground خلال ثوانٍ من startForegroundService
            if (!hasPermission(Manifest.permission.CAMERA) || !enterForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            started = true
            val now = System.currentTimeMillis()
            RecordingState.update {
                it.copy(isRecording = true, recordingStartedAt = now, segmentsSaved = 0, message = null)
            }
            thermalMonitor.start()
            initCamera()
        }
        // START_STICKY: إن أوقف النظام الخدمة لنقص الذاكرة يحاول إعادة تشغيلها
        return START_STICKY
    }

    override fun onDestroy() {
        if (!finished) {
            stopRequested = true
            finishService()
        }
        super.onDestroy()
    }

    // ======================= المعاينة (من الشاشة الرئيسية) =======================

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider, rotation: Int) {
        targetRotation = rotation
        preview.targetRotation = rotation
        videoCapture?.targetRotation = rotation // يُطبّق من المقطع التالي
        preview.setSurfaceProvider(surfaceProvider)
    }

    /** عند إغلاق الشاشة أو تفعيل الشاشة السوداء: نوقف المعاينة فقط ويستمر التسجيل. */
    fun detachPreview() {
        preview.setSurfaceProvider(null)
    }

    // ======================= الكاميرا =======================

    private fun initCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                if (bindCamera()) startNewSegment() else scheduleRetry(rebind = true)
            } catch (e: Exception) {
                Log.e(TAG, "فشل تهيئة الكاميرا", e)
                scheduleRetry(rebind = true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(): Boolean {
        val provider = cameraProvider ?: return false
        if (stopRequested) return false
        return try {
            val quality = when (settings.quality) {
                AppSettings.QUALITY_SD -> Quality.SD
                AppSettings.QUALITY_FHD -> Quality.FHD
                else -> Quality.HD
            }
            // إن لم يدعم الجهاز الجودة المطلوبة ننزل لأقرب جودة أقل
            val selector = QualitySelector.from(
                quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val recorder = Recorder.Builder().setQualitySelector(selector).build()
            val capture = VideoCapture.withOutput(recorder)
            capture.targetRotation = targetRotation
            preview.targetRotation = targetRotation

            provider.unbindAll()
            provider.bindToLifecycle(this, chooseCamera(provider), preview, capture)
            videoCapture = capture
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل ربط الكاميرا", e)
            RecordingState.update { it.copy(message = "تعذر فتح الكاميرا، جارٍ إعادة المحاولة…") }
            false
        }
    }

    private fun chooseCamera(provider: ProcessCameraProvider): CameraSelector {
        val wanted = if (settings.useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
        return try {
            if (provider.hasCamera(wanted)) wanted else CameraSelector.DEFAULT_BACK_CAMERA
        } catch (_: Exception) {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // ======================= التسجيل المقسّم =======================

    @SuppressLint("MissingPermission") // الإذن مفحوص قبل withAudioEnabled
    private fun startNewSegment() {
        if (stopRequested || activeRecording != null) return
        val capture = videoCapture ?: run { scheduleRetry(rebind = true); return }

        segmentMs = settings.segmentMinutes.coerceIn(1, 60) * 60_000L

        // 1) فحص المساحة وحذف الأقدم غير المقفل
        val needed = ClipStorage.estimateSegmentBytes(settings.quality, segmentMs)
        val cleanup = ClipStorage.ensureSpace(this, needed)
        val warning = if (cleanup.enoughSpace) null
        else "تنبيه: المساحة ممتلئة بمقاطع مقفلة أو الذاكرة شبه ممتلئة. ألغِ قفل بعض المقاطع أو ارفع الحد."

        // 2) ملف جديد باسم التاريخ والوقت، بحد زمني = مدة المقطع
        val file = ClipStorage.newClipFile(this)
        val options = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(segmentMs)
            .build()

        try {
            var pending = capture.output.prepareRecording(this, options)
            if (audioEnabled && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                pending = pending.withAudioEnabled()
            }
            activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                onRecordEvent(event, file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل بدء المقطع", e)
            activeRecording = null
            file.delete()
            scheduleRetry(rebind = true)
            return
        }

        currentFile = file
        // تجديد الـ WakeLock مع كل مقطع (بمهلة، حتى لا يبقى معلقاً لو انهار التطبيق)
        wakeLock.acquire(segmentMs + 10 * 60_000L)
        handler.removeCallbacks(segmentWatchdog)
        handler.postDelayed(segmentWatchdog, segmentMs + 20_000L)

        RecordingState.update {
            it.copy(
                isRecording = true,
                currentFile = file.name,
                segmentStartedAt = System.currentTimeMillis(),
                segmentMs = segmentMs,
                message = warning,
            )
        }
        updateNotification()
    }

    private fun onRecordEvent(event: VideoRecordEvent, file: File) {
        if (event !is VideoRecordEvent.Finalize) return

        handler.removeCallbacks(segmentWatchdog)
        activeRecording = null
        val error = event.error

        // ملف فارغ أو بلا بيانات صالحة => نحذفه
        if (error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA || !file.exists() || file.length() == 0L) {
            file.delete()
        } else {
            RecordingState.update { it.copy(segmentsSaved = it.segmentsSaved + 1) }
        }

        if (stopRequested) {
            finishService()
            return
        }

        when (error) {
            // انتهاء طبيعي للمقطع => ابدأ التالي فوراً
            VideoRecordEvent.Finalize.ERROR_NONE,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> startNewSegment()

            // امتلاء الذاكرة => حذف إضافي ثم المحاولة
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> {
                ClipStorage.ensureSpace(this, ClipStorage.estimateSegmentBytes(settings.quality, segmentMs) * 2)
                scheduleRetry(delay = 1_000L)
            }

            // الكاميرا انقطعت (تطبيق آخر أخذها مثلاً) أو خطأ آخر => إعادة ربط ومحاولة
            else -> {
                Log.w(TAG, "انتهى المقطع بخطأ $error", event.cause)
                scheduleRetry(rebind = true)
            }
        }
    }

    private fun scheduleRetry(delay: Long = RETRY_DELAY_MS, rebind: Boolean = false) {
        if (stopRequested) return
        rebindOnRetry = rebindOnRetry || rebind
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delay)
    }

    private fun retry() {
        if (stopRequested) return
        if (cameraProvider == null) {
            initCamera()
            return
        }
        if (rebindOnRetry || videoCapture == null) {
            rebindOnRetry = false
            if (!bindCamera()) {
                scheduleRetry(delay = 5_000L, rebind = true)
                return
            }
        }
        startNewSegment()
    }

    // ======================= الإيقاف =======================

    /** إيقاف بطلب المستخدم: ننهي المقطع الحالي بشكل سليم ثم نغلق الخدمة. */
    fun requestStop() {
        if (!started) {
            finishService()
            return
        }
        stopRequested = true
        handler.removeCallbacks(retryRunnable)
        val recording = activeRecording
        if (recording != null) {
            recording.stop() // سيصل Finalize ثم finishService()
            handler.postDelayed({ finishService() }, 5_000L) // احتياط
        } else {
            finishService()
        }
    }

    private fun finishService() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        activeRecording?.close()
        activeRecording = null
        try { cameraProvider?.unbindAll() } catch (_: Exception) { }
        thermalMonitor.stop()
        if (wakeLock.isHeld) wakeLock.release()
        RecordingState.update { RecorderStatus() }
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ======================= الحرارة =======================

    private fun onThermalChanged(level: Int, message: String?) {
        RecordingState.update { it.copy(thermalWarning = message) }
        updateNotification()
        // تنبيه منبثق فقط عند ارتفاع المستوى (وليس مع كل تغيّر درجة)
        if (message != null && level > lastThermalLevel) {
            postAlert("تنبيه حرارة — التسجيل مستمر", message)
        }
        lastThermalLevel = level
    }

    // ======================= الإشعارات =======================

    private fun enterForeground(): Boolean {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (audioEnabled) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "تعذر تشغيل الخدمة الأمامية", e)
            false
        }
    }

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "التسجيل المستمر", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "إشعار ثابت يوضح أن الكاميرا قيد التسجيل"
                    setShowBadge(false)
                }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "تنبيهات الحرارة", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopAction = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = currentFile?.let { "المقطع الحالي: ${it.name}" } ?: "جارٍ تجهيز الكاميرا…"
        val thermal = RecordingState.status.value.thermalWarning

        return NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("● الكاميرا قيد التسجيل")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (thermal != null) "$text\n$thermal" else text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notification, "إيقاف التسجيل", stopAction)
            .build()
    }

    private fun updateNotification() {
        if (started && !finished && canPostNotifications()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun postAlert(title: String, text: String) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
