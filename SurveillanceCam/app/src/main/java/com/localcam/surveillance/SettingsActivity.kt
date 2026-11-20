package com.localcam.surveillance

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

/** شاشة الإعدادات: المساحة القصوى، مدة المقطع، الجودة، الكاميرا، الصوت، والبطارية. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var etMaxGb: TextInputEditText
    private lateinit var etSegment: TextInputEditText
    private lateinit var rgQuality: RadioGroup
    private lateinit var rgCamera: RadioGroup
    private lateinit var swAudio: MaterialSwitch
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvStorageInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = AppSettings(this)

        etMaxGb = findViewById(R.id.etMaxGb)
        etSegment = findViewById(R.id.etSegment)
        rgQuality = findViewById(R.id.rgQuality)
        rgCamera = findViewById(R.id.rgCamera)
        swAudio = findViewById(R.id.swAudio)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        tvStorageInfo = findViewById(R.id.tvStorageInfo)

        // تعبئة القيم الحالية
        etMaxGb.setText(formatNumber(settings.maxStorageGb))
        etSegment.setText(settings.segmentMinutes.toString())
        rgQuality.check(
            when (settings.quality) {
                AppSettings.QUALITY_SD -> R.id.rbSd
                AppSettings.QUALITY_FHD -> R.id.rbFhd
                else -> R.id.rbHd
            }
        )
        rgCamera.check(if (settings.useFrontCamera) R.id.rbFront else R.id.rbBack)
        swAudio.isChecked = settings.recordAudio

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { BatteryHelper.showDialog(this) }
    }

    override fun onResume() {
        super.onResume()
        // تحديث الحالة بعد العودة من إعدادات النظام
        tvBatteryStatus.text = if (BatteryHelper.isUnrestricted(this))
            "✅ البطارية: غير مقيدة — ممتاز للتسجيل الطويل"
        else
            "⚠️ البطارية: مقيدة — قد يوقف النظام التسجيل بعد مدة"

        val dir = ClipStorage.clipsDir(this)
        val used = Formatter.formatShortFileSize(this, ClipStorage.totalSize(this))
        val free = Formatter.formatShortFileSize(this, dir.usableSpace)
        tvStorageInfo.text = "مجلد الحفظ:\n${dir.absolutePath}\n\nمساحة المقاطع: $used\nالمساحة الحرة في الجهاز: $free"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun save() {
        val gb = parseNumber(etMaxGb.text?.toString())
        if (gb == null || gb < 0.5f) {
            etMaxGb.error = "أدخل رقماً لا يقل عن 0.5"
            return
        }
        val minutes = parseNumber(etSegment.text?.toString())?.toInt()
        if (minutes == null || minutes !in 1..60) {
            etSegment.error = "من 1 إلى 60 دقيقة"
            return
        }

        settings.maxStorageGb = gb
        settings.segmentMinutes = minutes
        settings.quality = when (rgQuality.checkedRadioButtonId) {
            R.id.rbSd -> AppSettings.QUALITY_SD
            R.id.rbFhd -> AppSettings.QUALITY_FHD
            else -> AppSettings.QUALITY_HD
        }
        settings.useFrontCamera = rgCamera.checkedRadioButtonId == R.id.rbFront
        settings.recordAudio = swAudio.isChecked

        Toast.makeText(this, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** يقبل الأرقام العربية (٠١٢٣) والفاصلة العربية «٫» أو «,». */
    private fun parseNumber(text: String?): Float? {
        if (text.isNullOrBlank()) return null
        val normalized = buildString {
            for (c in text.trim()) {
                when (c) {
                    in '٠'..'٩' -> append('0' + (c - '٠'))
                    in '۰'..'۹' -> append('0' + (c - '۰'))
                    '٫', ',' -> append('.')
                    else -> append(c)
                }
            }
        }
        return normalized.toFloatOrNull()
    }

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
