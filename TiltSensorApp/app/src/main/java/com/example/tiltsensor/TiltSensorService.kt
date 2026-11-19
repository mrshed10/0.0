package com.example.tiltsensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * خدمة في المقدمة (Foreground Service) تقرأ الحساسات باستمرار حتى لو خرجت من التطبيق.
 * لا ترسم أي شيء على الشاشة ولا تستخدم نوافذ عائمة. الإشعار الوحيد صامت وبأقل أولوية،
 * وهو شرط إلزامي من نظام أندرويد لأي خدمة تعمل في الخلفية بشكل مستمر.
 */
class TiltSensorService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "TiltSensorService"
        private const val CHANNEL_ID = "tilt_sensor_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.tiltsensor.START"
        const val ACTION_STOP = "com.example.tiltsensor.STOP"
        const val ACTION_RECALIBRATE = "com.example.tiltsensor.RECALIBRATE"

        fun start(context: Context) {
            val i = Intent(context, TiltSensorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, TiltSensorService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun recalibrate(context: Context) {
            val i = Intent(context, TiltSensorService::class.java).setAction(ACTION_RECALIBRATE)
            context.startService(i)
        }
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    // نقرأ الحساسات في خيط منفصل حتى لا نثقل الخيط الرئيسي
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private val processor = TiltProcessor()
    private var listening = false

    // لتقليل عدد التحديثات المنشورة (حوالي 30 تحديث بالثانية كحد أقصى)
    private var lastPublishNs = 0L
    private val publishIntervalNs = 33_000_000L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECALIBRATE -> {
                sensorHandler?.post { processor.recalibrate() }
                if (!listening) goForegroundAndListen()
            }
            else -> goForegroundAndListen() // ACTION_START أو إعادة تشغيل من النظام (intent = null)
        }
        // إذا قتل النظام الخدمة يعيد تشغيلها تلقائياً
        return START_STICKY
    }

    private fun goForegroundAndListen() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "تعذر تشغيل الخدمة في المقدمة", e)
            stopSelf()
            return
        }
        startListening()
    }

    private fun startListening() {
        if (listening) return
        val acc = accelerometer
        val mag = magnetometer
        if (acc == null || mag == null) {
            Log.e(TAG, "الحساسات غير متوفرة: accelerometer=$acc magnetometer=$mag")
            stopSelf()
            return
        }

        val thread = HandlerThread("TiltSensorThread").apply { start() }
        sensorThread = thread
        val handler = Handler(thread.looper)
        sensorHandler = handler

        processor.recalibrate()
        sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, handler)
        sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME, handler)
        listening = true
        TiltState.setRunning(true)
        Log.i(TAG, "بدأت قراءة الحساسات")
    }

    private fun stopListening() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        listening = false
        TiltState.setRunning(false)
        Log.i(TAG, "توقفت قراءة الحساسات")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processor.onAccelerometer(event.values)
            Sensor.TYPE_MAGNETIC_FIELD -> processor.onMagnetometer(event.values)
            else -> return
        }

        val reading = processor.compute(System.currentTimeMillis()) ?: return

        if (event.timestamp - lastPublishNs < publishIntervalNs) return
        lastPublishNs = event.timestamp

        TiltState.publish(reading)

        // ===== هنا تضع منطقك الخاص للتفاعل مع الحركة =====
        // مثال: reading.horizontal == Horizontal.RIGHT  أو  reading.vertical == Vertical.UP
        // reading.pitch و reading.roll بالدرجات نسبةً لوضع المعايرة.
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD &&
            accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        ) {
            Log.w(TAG, "دقة المغناطيس منخفضة — حرّك الجهاز على شكل 8 للمعايرة")
        }
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN // بدون صوت ولا اهتزاز ولا ظهور منبثق
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }
}
