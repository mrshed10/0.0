package com.livetranslate.screen.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.livetranslate.screen.service.LiveTranslationService

/** نشاط شفاف يطلب موافقة التقاط الشاشة عند الحاجة (من الشريط العائم أو الخدمة). */
class ProjectionPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            LiveTranslationService.deliverProjection(this, r.resultCode, data)
        } else {
            LiveTranslationService.projectionDenied(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launcher.launch(captureIntent(this))
    }

    companion object {
        /** نطلب الشاشة الكاملة (وليس تطبيقاً واحداً) لأن الإحداثيات يجب أن تطابق الشاشة. */
        fun captureIntent(context: Context): Intent {
            val mpm = context.getSystemService(MediaProjectionManager::class.java)
            return if (Build.VERSION.SDK_INT >= 34) {
                mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                mpm.createScreenCaptureIntent()
            }
        }
    }
}
