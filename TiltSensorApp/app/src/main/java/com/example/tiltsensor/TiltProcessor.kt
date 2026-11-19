package com.example.tiltsensor

import android.hardware.SensorManager
import kotlin.math.abs

/**
 * يحوّل قراءات التسارع والمغناطيس الخام إلى زوايا ميلان منعّمة.
 *
 * مراحل التنعيم:
 * 1) مرشّح تمرير منخفض (Low-pass) على القيم الخام للتسارع والمغناطيس لإزالة الرجفة.
 * 2) حساب مصفوفة الدوران ثم الفرق الزاوي عن وضع المعايرة (يتفادى مشكلة قفل المحاور
 *    لأن الفرق دائماً قريب من الصفر مهما كانت طريقة مسك الجهاز).
 * 3) متوسط متحرك أُسّي (EMA) على الزوايا النهائية.
 * 4) منطقة ميتة + Hysteresis لتحديد الاتجاه بدون تذبذب.
 */
class TiltProcessor(
    /** معامل تنعيم القيم الخام (0..1) — الأصغر = أنعم وأبطأ */
    private val rawAlpha: Float = 0.15f,
    /** معامل تنعيم الزوايا (0..1) — الأصغر = أنعم وأبطأ */
    private val angleAlpha: Float = 0.2f,
    /** زاوية بدء اعتبار الميلان (درجات) */
    private val enterThreshold: Float = 12f,
    /** زاوية الرجوع للوسط (أقل من enter لمنع التذبذب) */
    private val exitThreshold: Float = 8f,
    /** عدد العينات لحساب وضع المعايرة عند البدء */
    private val calibrationSamples: Int = 15,
    /** اعكس الإشارات إذا وجدت الاتجاه معكوس على جهازك */
    private val invertPitch: Boolean = false,
    private val invertRoll: Boolean = false
) {
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnet = false

    private val rotation = FloatArray(9)
    private val inclination = FloatArray(9)
    private val baseline = FloatArray(9)
    private var hasBaseline = false

    private val calibAccum = FloatArray(9)
    private var calibCount = 0

    private val angleChange = FloatArray(3)

    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothAzimuth = 0f
    private var hasSmooth = false

    private var horizontal = Horizontal.CENTER
    private var vertical = Vertical.CENTER

    /** يعيد ضبط المعايرة: الوضع الحالي للجهاز يصبح هو الصفر */
    fun recalibrate() {
        hasBaseline = false
        calibCount = 0
        calibAccum.fill(0f)
        hasSmooth = false
        horizontal = Horizontal.CENTER
        vertical = Vertical.CENTER
    }

    fun onAccelerometer(values: FloatArray) {
        lowPass(values, gravity, hasGravity)
        hasGravity = true
    }

    fun onMagnetometer(values: FloatArray) {
        lowPass(values, geomagnetic, hasMagnet)
        hasMagnet = true
    }

    /**
     * يُستدعى بعد كل قراءة. يرجع null إذا لم تكتمل البيانات أو ما زالت المعايرة جارية.
     */
    fun compute(timestamp: Long): TiltReading? {
        if (!hasGravity || !hasMagnet) return null
        if (!SensorManager.getRotationMatrix(rotation, inclination, gravity, geomagnetic)) {
            return null // حالة سقوط حر أو مجال مغناطيسي غير صالح
        }

        if (!hasBaseline) {
            for (i in 0 until 9) calibAccum[i] += rotation[i]
            calibCount++
            if (calibCount >= calibrationSamples) {
                for (i in 0 until 9) baseline[i] = calibAccum[i] / calibCount
                orthonormalize(baseline)
                hasBaseline = true
            }
            return null
        }

        // الفرق الزاوي بين الوضع الحالي ووضع المعايرة: [azimuth, pitch, roll] بالراديان
        SensorManager.getAngleChange(angleChange, rotation, baseline)

        var azimuth = Math.toDegrees(angleChange[0].toDouble()).toFloat()
        var pitch = Math.toDegrees(angleChange[1].toDouble()).toFloat()
        var roll = Math.toDegrees(angleChange[2].toDouble()).toFloat()

        // اصطلاح: رفع الحافة العلوية = أعلى (+) ، إمالة الحافة اليمنى للأسفل = يمين (+)
        pitch = -pitch
        if (invertPitch) pitch = -pitch
        if (invertRoll) roll = -roll

        if (!hasSmooth) {
            smoothPitch = pitch
            smoothRoll = roll
            smoothAzimuth = azimuth
            hasSmooth = true
        } else {
            smoothPitch = emaAngle(smoothPitch, pitch, angleAlpha)
            smoothRoll = emaAngle(smoothRoll, roll, angleAlpha)
            smoothAzimuth = emaAngle(smoothAzimuth, azimuth, angleAlpha)
        }

        horizontal = classifyHorizontal(smoothRoll, horizontal)
        vertical = classifyVertical(smoothPitch, vertical)

        return TiltReading(
            pitch = smoothPitch,
            roll = smoothRoll,
            azimuth = smoothAzimuth,
            horizontal = horizontal,
            vertical = vertical,
            timestamp = timestamp
        )
    }

    private fun classifyHorizontal(roll: Float, current: Horizontal): Horizontal {
        val a = abs(roll)
        return when {
            a >= enterThreshold -> if (roll > 0) Horizontal.RIGHT else Horizontal.LEFT
            a <= exitThreshold -> Horizontal.CENTER
            else -> current // داخل منطقة الـ Hysteresis: حافظ على الحالة السابقة
        }
    }

    private fun classifyVertical(pitch: Float, current: Vertical): Vertical {
        val a = abs(pitch)
        return when {
            a >= enterThreshold -> if (pitch > 0) Vertical.UP else Vertical.DOWN
            a <= exitThreshold -> Vertical.CENTER
            else -> current
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray, initialized: Boolean) {
        if (!initialized) {
            for (i in 0 until 3) output[i] = input[i]
            return
        }
        for (i in 0 until 3) {
            output[i] = output[i] + rawAlpha * (input[i] - output[i])
        }
    }

    /** تنعيم زاوية مع مراعاة الالتفاف عند ±180 درجة */
    private fun emaAngle(prev: Float, target: Float, alpha: Float): Float {
        var diff = target - prev
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        var result = prev + alpha * diff
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

    /** تصحيح مصفوفة المتوسط لتبقى مصفوفة دوران صحيحة (Gram-Schmidt على الصفوف) */
    private fun orthonormalize(m: FloatArray) {
        val r0 = floatArrayOf(m[0], m[1], m[2])
        val r1 = floatArrayOf(m[3], m[4], m[5])
        normalize(r0)
        val d = r0[0] * r1[0] + r0[1] * r1[1] + r0[2] * r1[2]
        for (i in 0 until 3) r1[i] -= d * r0[i]
        normalize(r1)
        val r2 = floatArrayOf(
            r0[1] * r1[2] - r0[2] * r1[1],
            r0[2] * r1[0] - r0[0] * r1[2],
            r0[0] * r1[1] - r0[1] * r1[0]
        )
        for (i in 0 until 3) {
            m[i] = r0[i]
            m[3 + i] = r1[i]
            m[6 + i] = r2[i]
        }
    }

    private fun normalize(v: FloatArray) {
        val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len > 1e-6f) for (i in 0 until 3) v[i] /= len
    }
}
