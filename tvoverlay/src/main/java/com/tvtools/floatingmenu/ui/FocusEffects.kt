package com.tvtools.floatingmenu.ui

import android.view.View
import android.view.ViewGroup
import android.widget.AbsSeekBar
import android.widget.EditText

/** تكبير بسيط للعنصر المحدد ليظهر التركيز بوضوح من بعيد (يكمّل الإطار الأزرق في bg_tile). */
object FocusEffects {
    private const val FOCUS_SCALE = 1.05f
    private const val DURATION_MS = 140L

    fun apply(view: View) {
        val previous = view.onFocusChangeListener
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            previous?.onFocusChange(v, hasFocus)
            val scale = if (hasFocus) FOCUS_SCALE else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(DURATION_MS).start()
        }
    }

    /** يطبّق التأثير على كل العناصر القابلة للنقر والتركيز داخل الشجرة. */
    fun applyTree(root: View) {
        if (root.isFocusable && root.isClickable && root !is EditText && root !is AbsSeekBar) {
            apply(root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) applyTree(root.getChildAt(i))
        }
    }
}
