package app.safar.browser.ui.translate

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * مربع زجاجي عائم لترجمة جزء محدد من الشاشة: يُسحب من المقبض ويُغيَّر حجمه من الزاوية.
 * - "ترجم": مرة واحدة.  - "مباشر": تحديث دوري (مفيد للفيديو/الألعاب).  - "العين": إظهار ما تحته لإعادة التصويب.
 * ملاحظة: capturePixels تلتقط محتوى Gecko فقط، لذا المربع نفسه لا يظهر في اللقطة.
 */
@Composable
fun FloatingGlassBox(onTranslate: suspend (Rect) -> String, onClose: () -> Unit) {
    val density = LocalDensity.current
    val headerPx = with(density) { 40.dp.toPx() }
    val minW = with(density) { 160.dp.toPx() }
    val minH = with(density) { 110.dp.toPx() }

    var parent by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset(with(density) { 24.dp.toPx() }, with(density) { 120.dp.toPx() })) }
    var size by remember { mutableStateOf(Offset(with(density) { 300.dp.toPx() }, with(density) { 190.dp.toPx() })) }
    var result by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun runOnce() {
        if (busy) return
        busy = true
        val r = Rect(pos.x.roundToInt(), (pos.y + headerPx).roundToInt(), (pos.x + size.x).roundToInt(), (pos.y + size.y).roundToInt())
        result = runCatching { onTranslate(r) }.getOrElse { "تعذرت الترجمة: ${it.message}" }.ifBlank { "لم يُعثر على نص في هذه المنطقة" }
        showResult = true
        busy = false
    }

    LaunchedEffect(live) {
        while (live) { runOnce(); delay(2500) }
    }

    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxSize().onSizeChanged { parent = it }, contentAlignment = AbsoluteAlignment.TopLeft) {
        Column(
            Modifier
                .absoluteOffset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) } // إحداثيات فيزيائية (لا تنعكس في RTL)
                .size(with(density) { size.x.toDp() }, with(density) { size.y.toDp() })
                .clip(shape)
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .2f), Color(0x8890CAF9))),
                    shape,
                ),
        ) {
            // شريط العنوان (مقبض السحب)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xCC1E2A38), Color(0xAA2B3D52))))
                    .pointerInput(parent) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pos = Offset(
                                (pos.x + drag.x).coerceIn(0f, (parent.width - size.x).coerceAtLeast(0f)),
                                (pos.y + drag.y).coerceIn(0f, (parent.height - size.y).coerceAtLeast(0f)),
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DragIndicator, null, tint = Color.White, modifier = Modifier.padding(start = 6.dp))
                Box(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                IconButton(onClick = { scope.launch { runOnce() } }) { Icon(Icons.Filled.Translate, "ترجم", tint = Color.White) }
                IconButton(onClick = { live = !live }) {
                    Icon(if (live) Icons.Outlined.Pause else Icons.Filled.PlayArrow, "مباشر", tint = if (live) Color(0xFF80D8FF) else Color.White)
                }
                IconButton(onClick = { showResult = !showResult }) {
                    Icon(if (showResult) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "إظهار ما تحته", tint = Color.White)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
            }
            // جسم المربع: شفاف لتصويب المنطقة، أو زجاج داكن يعرض الترجمة
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (showResult) Brush.verticalGradient(listOf(Color(0xE6101820), Color(0xD91A2633)))
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .04f)))
                    ),
            ) {
                if (showResult) {
                    Text(
                        result, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
                // مقبض تغيير الحجم
                Icon(
                    Icons.Filled.OpenInFull, "تغيير الحجم", tint = Color.White.copy(alpha = .9f),
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomRight)
                        .size(28.dp)
                        .padding(4.dp)
                        .pointerInput(parent) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                size = Offset(
                                    (size.x + drag.x).coerceIn(minW, parent.width.toFloat()),
                                    (size.y + drag.y).coerceIn(minH, parent.height.toFloat()),
                                )
                            }
                        },
                )
            }
        }
    }
}
