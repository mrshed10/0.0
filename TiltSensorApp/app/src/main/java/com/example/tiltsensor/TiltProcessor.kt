package com.example.tiltsensor

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * يحسب زوايا الميلان من حساس التسارع وحده (بدون مغناطيس أو جيروسكوب).
 *
 * الفكرة: عندما يكون الجهاز شبه ساكن، حساس التسارع يقيس اتجاه الجاذبية.
 * نقارن اتجاه الجاذبية الحالي باتجاهها وقت المعايرة، ونحسب الدوران بينهما
 * ثم نفكّه إلى مكوّنين: أعلى/أسفل (حول محور X للجهاز) ويمين/يسار (المحور العمودي عليه).
 * هذه الطريقة تعمل سواء كان الجوال مسطّحاً على الطاولة أو واقفاً بيدك، بدون قفل محاور.
 *
 * مراحل التنعيم:
 * 1) مرشّح تمرير منخفض (Low-pass) على القيم الخام لعزل الجاذبية وإزالة الرجفة.
 * 2) تجاهل القراءات أثناء الهز العنيف (عندما يبتعد مقدار التسارع كثيراً عن 9.81).
 * 3) متوسط متحرك أُسّي (EMA) على الزوايا النهائية.
 * 4) منطقة ميتة + Hysteresis لتحديد الاتجاه بدون تذبذب.
 *
 * ملاحظة: بدون جيروسكوب لا يمكن قياس الدوران حول محور الجاذبية (مثل لفّ الجوال
 * على الطاولة كالبوصلة)، لكن الميلان يمين/يسار وأعلى/أسفل يُقاس بشكل كامل.
 */
class TiltProcessor(
    /** معامل تنعيم القيم الخام (0..1) — الأصغر = أنعم وأبطأ */
    private val rawAlpha: Float = 0.12f,
    /** معامل تنعيم الزوايا (0..1) — الأصغر = أنعم وأبطأ */
    private val angleAlpha: Float = 0.2f,
    /** زاوية بدء اعتبار الميلان (درجات) */
    private val enterThreshold: Float = 12f,
    /** زاوية الرجوع للوسط (أقل من enter لمنع التذبذب) */
    private val exitThreshold: Float = 8f,
    /** عدد العينات لحساب وضع المعايرة عند البدء */
    private val calibrationSamples: Int = 20,
    /** أقصى فرق مسموح بين مقدار التسارع والجاذبية (م/ث²) قبل اعتبار القراءة هزّة */
    private val shakeTolerance: Float = 3.5f,
    /** اعكس الإشارات إذا وجدت الاتجاه معكوس على جهازك */
    private val invertPitch: Boolean = false,
    private val invertRoll: Boolean = false
) {
    private val gravity = FloatArray(3)
    private var hasGravity = false

    // وضع المعايرة: متجه الجاذبية الأساسي + محوران عموديان عليه
    private val baseG = FloatArray(3)
    private val axisPitch = FloatArray(3)   // محور الميلان أعلى/أسفل
    private val axisRoll = FloatArray(3)    // محور الميلان يمين/يسار
    private var hasBaseline = false

    private val calibAccum = FloatArray(3)
    private var calibCount = 0

    private var smoothPitch = 0f
    private var smoothRoll = 0f
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
        if (!hasGravity) {
            for (i in 0 until 3) gravity[i] = values[i]
            hasGravity = true
            return
        }
        for (i in 0 until 3) {
            gravity[i] = gravity[i] + rawAlpha * (values[i] - gravity[i])
        }
    }

    /**
     * يُستدعى بعد كل قراءة. يرجع null إذا لم تكتمل البيانات أو ما زالت المعايرة جارية
     * أو كان الجهاز يُهزّ بقوة.
     */
    fun compute(rawMagnitude: Float, timestamp: Long): TiltReading? {
        if (!hasGravity) return null

        // أثناء الهز أو السقوط الحر القراءة لا تمثل الجاذبية؛ نتجاهلها
        if (abs(rawMagnitude - SensorManager.GRAVITY_EARTH) > shakeTolerance) return null

        val g = FloatArray(3) { gravity[it] }
        if (!normalize(g)) return null

        if (!hasBaseline) {
            for (i in 0 until 3) calibAccum[i] += g[i]
            calibCount++
            if (calibCount >= calibrationSamples) {
                for (i in 0 until 3) baseG[i] = calibAccum[i] / calibCount
                if (!normalize(baseG)) {
                    recalibrate()
                    return null
                }
                buildAxes()
                hasBaseline = true
            }
            return null
        }

        // الدوران من الجاذبية الأساسية إلى الحالية: المحور = baseG × g ، الزاوية = atan2(|×|, ·)
        val cx = baseG[1] * g[2] - baseG[2] * g[1]
        val cy = baseG[2] * g[0] - baseG[0] * g[2]
        val cz = baseG[0] * g[1] - baseG[1] * g[0]
        val crossLen = sqrt(cx * cx + cy * cy + cz * cz)
        val dot = baseG[0] * g[0] + baseG[1] * g[1] + baseG[2] * g[2]
        val angle = atan2(crossLen, dot) // راديان 0..π

        var pitch = 0f
        var roll = 0f
        if (crossLen > 1e-6f) {
            val k = angle / crossLen
            val rx = cx * k
            val ry = cy * k
            val rz = cz * k
            // إسقاط متجه الدوران على المحورين
            val pRad = rx * axisPitch[0] + ry * axisPitch[1] + rz * axisPitch[2]
            val rRad = rx * axisRoll[0] + ry * axisRoll[1] + rz * axisRoll[2]
            // اصطلاح: رفع الحافة العلوية = أعلى (+) ، إمالة الحافة اليمنى للأسفل = يمين (+)
            pitch = -Math.toDegrees(pRad.toDouble()).toFloat()
            roll = -Math.toDegrees(rRad.toDouble()).toFloat()
        }
        if (invertPitch) pitch = -pitch
        if (invertRoll) roll = -roll

        if (!hasSmooth) {
            smoothPitch = pitch
            smoothRoll = roll
            hasSmooth = true
        } else {
            smoothPitch += angleAlpha * (pitch - smoothPitch)
            smoothRoll += angleAlpha * (roll - smoothRoll)
        }

        horizontal = classifyHorizontal(smoothRoll, horizontal)
        vertical = classifyVertical(smoothPitch, vertical)

        return TiltReading(
            pitch = smoothPitch,
            roll = smoothRoll,
            horizontal = horizontal,
            vertical = vertical,
            timestamp = timestamp
        )
    }

    /**
     * يبني محورين عموديين على الجاذبية الأساسية:
     * axisPitch = محور X للجهاز بعد إزالة مكوّن الجاذبية منه (أعلى/أسفل)
     * axisRoll  = baseG × axisPitch (يمين/يسار)
     * إذا كان الجهاز على جنبه (X موازي للجاذبية) نستخدم محور Y بدلاً منه.
     */
    private fun buildAxes() {
        val ref = if (abs(baseG[0]) < 0.9f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
        val d = ref[0] * baseG[0] + ref[1] * baseG[1] + ref[2] * baseG[2]
        for (i in 0 until 3) axisPitch[i] = ref[i] - d * baseG[i]
        normalize(axisPitch)
        axisRoll[0] = baseG[1] * axisPitch[2] - baseG[2] * axisPitch[1]
        axisRoll[1] = baseG[2] * axisPitch[0] - baseG[0] * axisPitch[2]
        axisRoll[2] = baseG[0] * axisPitch[1] - baseG[1] * axisPitch[0]
        normalize(axisRoll)
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

    /** يحوّل المتجه لطول 1. يرجع false إذا كان المتجه صفرياً تقريباً. */
    private fun normalize(v: FloatArray): Boolean {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len < 1e-6f) return false
        for (i in 0 until 3) v[i] /= len
        return true
    }
}
