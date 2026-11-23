package app.safar.browser.ui.browser

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * إيماءات الشريط: سحب أفقي (تبديل التبويبات أو رجوع/تقدّم) وسحب للأعلى (فتح مبدّل التبويبات).
 * @param onHorizontal +1 = سحب لليسار، -1 = سحب لليمين
 */
fun Modifier.barGestures(
    enabled: Boolean,
    onHorizontal: (Int) -> Unit,
    onSwipeUp: (() -> Unit)?,
): Modifier = if (!enabled && onSwipeUp == null) this else pointerInput(enabled, onSwipeUp != null) {
    var dx = 0f
    var dy = 0f
    val threshold = 56.dp.toPx()
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDragEnd = {
            if (enabled && abs(dx) > abs(dy) && abs(dx) > threshold) onHorizontal(if (dx < 0) 1 else -1)
            else if (onSwipeUp != null && dy < -threshold && abs(dy) > abs(dx)) onSwipeUp()
        },
    ) { change, drag ->
        change.consume()
        dx += drag.x; dy += drag.y
    }
}
