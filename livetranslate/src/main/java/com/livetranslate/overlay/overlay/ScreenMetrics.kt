package com.livetranslate.overlay.overlay

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** الحجم الحقيقي الكامل للشاشة (بما فيه شريطا الحالة والتنقل) بالبكسل. */
data class ScreenMetrics(val width: Int, val height: Int, val densityDpi: Int) {
    companion object {
        fun of(context: Context): ScreenMetrics {
            val wm = context.getSystemService(WindowManager::class.java)
            val dpi = context.resources.displayMetrics.densityDpi
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.maximumWindowMetrics.bounds
                ScreenMetrics(b.width(), b.height(), dpi)
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
                ScreenMetrics(dm.widthPixels, dm.heightPixels, dpi)
            }
        }
    }
}
