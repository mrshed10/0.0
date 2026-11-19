package com.example.tiltsensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** اتجاه الميلان يمين/يسار */
enum class Horizontal(val label: String) { LEFT("يسار"), CENTER("وسط"), RIGHT("يمين") }

/** اتجاه الميلان أعلى/أسفل */
enum class Vertical(val label: String) { UP("أعلى"), CENTER("وسط"), DOWN("أسفل") }

/** قراءة واحدة بعد التنعيم. الزوايا بالدرجات ونسبةً لوضع المعايرة. */
data class TiltReading(
    val pitch: Float,      // أعلى(+)/أسفل(-)
    val roll: Float,       // يمين(+)/يسار(-)
    val azimuth: Float,    // دوران حول المحور العمودي (البوصلة)
    val horizontal: Horizontal,
    val vertical: Vertical,
    val timestamp: Long
)

/**
 * مخزن مشترك بين الخدمة والواجهة (نفس العملية).
 * أي جزء من التطبيق يقدر يقرأ TiltState.reading ويتفاعل مع الحركة.
 */
object TiltState {
    private val _reading = MutableStateFlow<TiltReading?>(null)
    val reading: StateFlow<TiltReading?> = _reading.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun publish(r: TiltReading) {
        _reading.value = r
    }

    internal fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) _reading.value = null
    }
}
