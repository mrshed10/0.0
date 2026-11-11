package com.corerush.game

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * غلاف أندرويد للعبة Core Rush: يعرض core-rush/index.html من الـ assets داخل WebView
 * بملء الشاشة، ويوفّر جسراً للاهتزاز، ويربط زر الرجوع بالإيقاف المؤقت.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isHapticFeedbackEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true            // حفظ الإعدادات وأفضل مرحلة
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                textZoom = 100                      // تجاهل تكبير خط النظام حتى لا تنكسر الواجهة
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            addJavascriptInterface(Bridge(this@MainActivity), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                // لا خروج من اللعبة إلى أي رابط خارجي
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !request.url.toString().startsWith("file:///android_asset/")
            }
        }
        setContentView(web)
        hideSystemBars()
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.onAndroidBack ? window.onAndroidBack() : 'exit')") { result ->
            if (result == null || result.contains("exit")) finish()
        }
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** يُستدعى من JavaScript: AndroidBridge.vibrate("30") أو "60,40,120" (اهتزاز، توقف، اهتزاز...) */
    private class Bridge(context: Context) {
        private val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

        @JavascriptInterface
        fun vibrate(pattern: String) {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val parts = pattern.split(',').mapNotNull { it.trim().toLongOrNull()?.coerceIn(0L, 1000L) }
            if (parts.isEmpty()) return
            try {
                if (parts.size == 1) {
                    v.vibrate(VibrationEffect.createOneShot(parts[0].coerceAtLeast(1L), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L) + parts.toLongArray(), -1))
                }
            } catch (_: Exception) {
            }
        }
    }
}
