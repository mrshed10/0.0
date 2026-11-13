package com.livetranslate.overlay

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.livetranslate.overlay.service.OverlayService

/**
 * نشاط شفاف بلا واجهة (Theme.LiveTranslate.Transparent).
 * - ACTION_REQUEST_CAPTURE: يعرض نافذة موافقة النظام لالتقاط الشاشة ويمرّر النتيجة للخدمة.
 * - ACTION_OPEN_PICKER: يُستدعى من زر الإعدادات السريعة لفتح بطاقة الترجمة مباشرة.
 * وجوده في المقدمة يجعل تشغيل الخدمة الأمامية مسموحاً حتى على Android 14+.
 */
class OverlayTrampolineActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val intent = if (result.resultCode == RESULT_OK && data != null) {
            OverlayService.intent(this, OverlayService.ACTION_PROJECTION_GRANTED)
                .putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        } else {
            OverlayService.intent(this, OverlayService.ACTION_PROJECTION_DENIED)
        }
        ContextCompat.startForegroundService(this, intent)
        finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // نتيجة الموافقة ستصل عبر captureLauncher
        when (intent?.action) {
            ACTION_REQUEST_CAPTURE -> {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                captureLauncher.launch(mpm.createScreenCaptureIntent())
            }
            ACTION_OPEN_PICKER -> {
                if (Settings.canDrawOverlays(this)) {
                    ContextCompat.startForegroundService(
                        this, OverlayService.intent(this, OverlayService.ACTION_OPEN_PICKER),
                    )
                } else {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                finishQuietly()
            }
            else -> finishQuietly()
        }
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_REQUEST_CAPTURE = "com.livetranslate.overlay.REQUEST_CAPTURE"
        const val ACTION_OPEN_PICKER = "com.livetranslate.overlay.TRAMPOLINE_OPEN_PICKER"
    }
}
