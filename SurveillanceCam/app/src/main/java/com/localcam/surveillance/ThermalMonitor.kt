package com.localcam.surveillance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * يراقب حرارة الجهاز بطريقتين:
 *  1) حالة الحرارة الرسمية من النظام (أندرويد 10+).
 *  2) حرارة البطارية (كل الأجهزة).
 * لا يوقف التسجيل أبداً — فقط يُبلغ بمستوى التحذير: 0 طبيعي، 1 دافئ، 2 ساخن جداً.
 */
class ThermalMonitor(
    private val context: Context,
    private val onChange: (level: Int, message: String?) -> Unit,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var thermalStatus = 0
    private var batteryTempC = 0f
    private var lastMessage: String? = null
    private var running = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            batteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            evaluate()
        }
    }

    fun start() {
        if (running) return
        running = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                thermalStatus = status
                evaluate()
            }
            powerManager.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            thermalListener = listener
        }
        // بث البطارية بث نظامي محمي، لذا التسجيل كـ EXPORTED آمن
        ContextCompat.registerReceiver(
            context, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun stop() {
        if (!running) return
        running = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { powerManager.removeThermalStatusListener(it) }
        }
        thermalListener = null
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
    }

    private fun evaluate() {
        val severeBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        val moderateBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

        val level = when {
            severeBySystem || batteryTempC >= 47f -> 2
            moderateBySystem || batteryTempC >= 42f -> 1
            else -> 0
        }
        val temp = if (batteryTempC > 0f) " (البطارية ${batteryTempC.toInt()}°م)" else ""
        val message = when (level) {
            2 -> "حرارة الجهاز مرتفعة جداً$temp. التسجيل مستمر — فعّل الشاشة السوداء وأبعد الجهاز عن الشمس والشاحن السريع."
            1 -> "الجهاز دافئ$temp. التسجيل مستمر — يُنصح بتفعيل الشاشة السوداء."
            else -> null
        }
        if (message != lastMessage) {
            lastMessage = message
            onChange(level, message)
        }
    }
}
