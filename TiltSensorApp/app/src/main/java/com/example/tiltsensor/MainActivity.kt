package com.example.tiltsensor

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvDirection: TextView
    private lateinit var tvValues: TextView
    private lateinit var btnCalibrate: Button
    private lateinit var btnToggle: Button

    // طلب إذن الإشعارات (أندرويد 13+). الخدمة تعمل سواء وافق المستخدم أو رفض؛
    // الرفض فقط يخفي الإشعار الصامت من شريط الإشعارات.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDirection = findViewById(R.id.tvDirection)
        tvValues = findViewById(R.id.tvValues)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnToggle = findViewById(R.id.btnToggle)

        if (!hasRequiredSensors()) {
            tvDirection.setText(R.string.no_sensors)
            btnCalibrate.isEnabled = false
            btnToggle.isEnabled = false
            return
        }

        btnCalibrate.setOnClickListener {
            TiltSensorService.recalibrate(this)
        }

        btnToggle.setOnClickListener {
            if (TiltState.running.value) {
                TiltSensorService.stop(this)
            } else {
                TiltSensorService.start(this)
            }
        }

        // البدء تلقائياً فور فتح التطبيق
        startTrackingAutomatically()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    TiltState.reading.collect { r ->
                        if (r == null) {
                            tvDirection.setText(R.string.waiting)
                            tvValues.text = ""
                        } else {
                            tvDirection.text = "${r.horizontal.label} / ${r.vertical.label}"
                            tvValues.text = String.format(
                                Locale.US,
                                "يمين/يسار (Roll):  %6.1f°\nأعلى/أسفل (Pitch): %6.1f°\nالدوران (Azimuth): %6.1f°",
                                r.roll, r.pitch, r.azimuth
                            )
                        }
                    }
                }
                launch {
                    TiltState.running.collect { running ->
                        btnToggle.setText(
                            if (running) R.string.stop_service else R.string.start_service
                        )
                        btnCalibrate.isEnabled = running
                    }
                }
            }
        }
    }

    private fun startTrackingAutomatically() {
        TiltSensorService.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasRequiredSensors(): Boolean {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val ok = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        if (!ok) Toast.makeText(this, R.string.no_sensors, Toast.LENGTH_LONG).show()
        return ok
    }
}
