package com.livetranslate.overlay.overlay

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient

/**
 * يرسم الإطار المضيء متعدد الألوان حول حواف الشاشة (مثل الفيديو):
 * عدة خطوط متراكبة بعرض متزايد وشفافية متناقصة تعطي توهجاً ناعماً نحو الداخل،
 * مع تدرّج دائري (SweepGradient) يدور ببطء.
 */
class EdgeGlowPainter(private val density: Float) {

    private val colors = intArrayOf(
        0xFF4285F4.toInt(), // أزرق
        0xFFA142F4.toInt(), // بنفسجي
        0xFFF06292.toInt(), // وردي
        0xFFFFA726.toInt(), // برتقالي
        0xFFFFEE58.toInt(), // أصفر
        0xFF66BB6A.toInt(), // أخضر
        0xFF4285F4.toInt(),
    )

    /** (عرض التوهج المرئي بالـ dp، الشفافية 0..255) من الحافة نحو الداخل. */
    private val layers = listOf(3f to 255, 8f to 150, 16f to 80, 28f to 42, 44f to 20)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val matrix = Matrix()
    private val rect = RectF()
    private var shader: SweepGradient? = null
    private var shaderCx = Float.NaN
    private var shaderCy = Float.NaN

    /**
     * @param bounds حدود الشاشة بإحداثيات اللوحة.
     * @param cornerRadius نصف قطر زوايا الشاشة الفعلي (بالبكسل).
     * @param rotation زاوية دوران التدرّج.
     * @param intensity 0..1 للظهور/الاختفاء التدريجي.
     */
    fun draw(canvas: Canvas, bounds: RectF, cornerRadius: Float, rotation: Float, intensity: Float) {
        if (intensity <= 0f) return
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        if (shader == null || cx != shaderCx || cy != shaderCy) {
            shader = SweepGradient(cx, cy, colors, null)
            shaderCx = cx
            shaderCy = cy
        }
        matrix.setRotate(rotation, cx, cy)
        shader!!.setLocalMatrix(matrix)
        paint.shader = shader

        path.reset()
        path.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        for ((widthDp, alpha) in layers) {
            // نصف الخط يقع خارج الشاشة، فالعرض المرئي = widthDp
            paint.strokeWidth = widthDp * density * 2f
            paint.alpha = (alpha * intensity).toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
        }
    }

    /** نسخة مصغّرة للمعاينة داخل التطبيق (بطاقة بزوايا دائرية). */
    fun drawPreview(canvas: Canvas, w: Float, h: Float, cornerRadius: Float, rotation: Float) {
        rect.set(0f, 0f, w, h)
        canvas.save()
        path.reset()
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(path)
        draw(canvas, rect, cornerRadius, rotation, 1f)
        canvas.restore()
    }
}
