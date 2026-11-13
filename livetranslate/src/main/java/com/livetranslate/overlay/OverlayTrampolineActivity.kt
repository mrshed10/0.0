package com.livetranslate.overlay

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.livetranslate.overlay.a11y.TranslateAccessibilityService
import com.livetranslate.overlay.service.OverlayService

/**
 * نشاط شفاف بلا واجهة (Theme.LiveTranslate.Transparent):
 * - ACTION_START: يعرض موافقة النظام لالتقاط الشاشة ثم يشغّل الترجمة مباشرة.
 * - ACTION_TOGGLE (زر الإعدادات السريعة): يوقف الترجمة إن كانت تعمل، وإلا يبدؤها.
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
            null
        }
        if (intent != null) ContextCompat.startForegroundService(this, intent)
        finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // نتيجة الموافقة ستصل عبر captureLauncher
        when (intent?.action) {
            ACTION_TOGGLE -> if (OverlayService.running.value) {
                startService(OverlayService.intent(this, OverlayService.ACTION_STOP))
                finishQuietly()
            } else {
                start()
            }
            ACTION_START -> start()
            else -> finishQuietly()
        }
    }

    private fun start() {
        if (!canShowOverlay(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finishQuietly()
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_START = "com.livetranslate.overlay.START"
        const val ACTION_TOGGLE = "com.livetranslate.overlay.TOGGLE"

        fun canShowOverlay(context: Context) =
            Settings.canDrawOverlays(context) || TranslateAccessibilityService.instance != null

        fun startIntent(context: Context) = Intent(context, OverlayTrampolineActivity::class.java)
            .setAction(ACTION_START)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
