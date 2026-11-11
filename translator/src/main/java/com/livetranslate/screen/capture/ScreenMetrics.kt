package com.livetranslate.screen.capture

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.livetranslate.screen.core.Box

data class ScreenSize(val width: Int, val height: Int, val densityDpi: Int) {
    val bounds: Box get() = Box(0, 0, width, height)
}

object ScreenMetrics {
    fun real(context: Context): ScreenSize {
        val wm = context.getSystemService(WindowManager::class.java)
        val dpi = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            ScreenSize(b.width(), b.height(), dpi)
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            ScreenSize(m.widthPixels, m.heightPixels, dpi)
        }
    }
}
