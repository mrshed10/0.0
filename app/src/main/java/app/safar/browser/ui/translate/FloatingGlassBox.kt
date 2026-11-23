package app.safar.browser.ui.translate

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.browser.data.OverlayStyle
import app.safar.browser.translate.LiveTranslateController.RegionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * مربع عائم لتحديد منطقة وترجمتها:
 *  - الإطار نفسه شفاف تماماً (حدود رفيعة فقط) فلا يغطي الرسم.
 *  - في المانغا/الصور تُرسم الترجمة داخل الفقاعات في الصفحة نفسها؛ المربع يعرض حالة مختصرة فقط.
 *  - في النصوص تُعرض النتيجة فوق خلفية بعتامة قابلة للتعديل (0..35%) أو كنص فقط بتوهج خفيف.
 *  - شريط علوي: سحب، ترجمة، مباشر، شفافية، إظهار/إخفاء بلمسة، إغلاق. الزاوية لتغيير الحجم.
 */
@Composable
fun FloatingGlassBox(
    onTranslate: suspend (Rect) -> RegionResult,
    opacity: Int,
    style: OverlayStyle,
    visible: Boolean,
    onOpacityChange: (Int) -> Unit,
    onStyleCycle: () -> Unit,
    onToggleVisible: () -> Unit,
    /** بدء تحريك/تكبير المربع: يمسح الترجمات المعروضة فوراً. */
    onInteractionStart: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val headerPx = with(density) { 36.dp.toPx() }
    val minW = with(density) { 140.dp.toPx() }
    val minH = with(density) { 90.dp.toPx() }

    var parent by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset(with(density) { 24.dp.toPx() }, with(density) { 120.dp.toPx() })) }
    var size by remember { mutableStateOf(Offset(with(density) { 300.dp.toPx() }, with(density) { 220.dp.toPx() })) }
    var result by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var showOpacity by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun runOnce() {
        if (busy) return
        busy = true
        val r = Rect(pos.x.roundToInt(), (pos.y + headerPx).roundToInt(), (pos.x + size.x).roundToInt(), (pos.y + size.y).roundToInt())
        runCatching { onTranslate(r) }
            .onSuccess { res ->
                if (res.placedInPage > 0) { result = ""; info = "✓ ${res.placedInPage} داخل الفقاعات" }
                else if (res.text.isBlank()) { result = ""; info = "لا يوجد نص هنا — حرّك المربع فوق الفقاعة" }
                else { result = res.text; info = null }
            }
            .onFailure { result = ""; info = "تعذرت الترجمة: ${it.message}" }
        busy = false
    }

    LaunchedEffect(live) { while (live) { runOnce(); delay(2500) } }

    // التقاط واحد فقط بعد إفلات الإصبع (Debounce 350ms)، مع مسح فوري عند بدء التحريك/التكبير
    val pending = remember { arrayOfNulls<Job>(1) }
    fun interactionStarted() {
        pending[0]?.cancel()
        result = ""; info = null
        onInteractionStart()
    }
    fun interactionEnded() {
        pending[0]?.cancel()
        pending[0] = scope.launch { delay(350); runOnce() }
    }

    val shape = RoundedCornerShape(14.dp)
    val alpha = opacity.coerceIn(0, 35) / 100f
    val lightText = Color.White
    val textShadow = Shadow(Color.Black, Offset(0f, 0f), 6f)

    Box(Modifier.fillMaxSize().onSizeChanged { parent = it }, contentAlignment = AbsoluteAlignment.TopLeft) {
        Column(
            Modifier
                .absoluteOffset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(with(density) { size.x.toDp() }, with(density) { size.y.toDp() }),
        ) {
            // شريط التحكم (مقبض السحب) — مدمج وصغير
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Color(0xB31E2A38))
                    .pointerInput(parent) {
                        detectDragGestures(
                            onDragStart = { interactionStarted() },
                            onDragEnd = { interactionEnded() },
                            onDragCancel = { interactionEnded() },
                        ) { change, drag ->
                            change.consume()
                            pos = Offset(
                                (pos.x + drag.x).coerceIn(0f, (parent.width - size.x).coerceAtLeast(0f)),
                                (pos.y + drag.y).coerceIn(0f, (parent.height - size.y).coerceAtLeast(0f)),
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DragIndicator, null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                Box(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                SmallBtn({ scope.launch { runOnce() } }) { Icon(Icons.Filled.Translate, "ترجم", tint = Color.White) }
                SmallBtn({ live = !live }) {
                    Icon(if (live) Icons.Outlined.Pause else Icons.Filled.PlayArrow, "مباشر", tint = if (live) Color(0xFF80D8FF) else Color.White)
                }
                SmallBtn({ showOpacity = !showOpacity }) { Icon(Icons.Outlined.Opacity, "الشفافية", tint = Color.White) }
                SmallBtn(onStyleCycle) {
                    Text(when (style) { OverlayStyle.CLEAN -> "نظف"; OverlayStyle.TRANSLUCENT -> "شفاف"; OverlayStyle.TEXT_ONLY -> "نص" },
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                SmallBtn(onToggleVisible) {
                    Icon(if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "إظهار/إخفاء", tint = Color.White)
                }
                SmallBtn(onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
            }
            if (showOpacity) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xB31E2A38)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${opacity}%", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(34.dp))
                    Slider(
                        value = opacity.toFloat(), onValueChange = { onOpacityChange(it.roundToInt()) },
                        valueRange = 0f..35f, steps = 6, modifier = Modifier.weight(1f).height(28.dp),
                    )
                }
            }
            // جسم المربع: شفاف تماماً — حدود رفيعة فقط لتحديد المنطقة
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.5.dp, Color.White.copy(alpha = .9f), shape)
                    .border(3.dp, Color.Black.copy(alpha = .25f), shape)
                    .clip(shape)
                    .background(if (visible && result.isNotEmpty() && style != OverlayStyle.TEXT_ONLY) Color(0xFF101820).copy(alpha = alpha) else Color.Transparent),
            ) {
                if (visible && result.isNotEmpty()) {
                    Text(
                        result,
                        style = TextStyle(
                            color = lightText, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start, shadow = textShadow,
                        ),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                    )
                }
                info?.let {
                    Text(
                        it, color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(AbsoluteAlignment.BottomLeft).padding(6.dp)
                            .clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                // مقبض تغيير الحجم
                Icon(
                    Icons.Filled.OpenInFull, "تغيير الحجم", tint = Color.White,
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomRight)
                        .size(28.dp)
                        .padding(4.dp)
                        .background(Color(0x66000000), RoundedCornerShape(6.dp))
                        .pointerInput(parent) {
                            detectDragGestures(
                                onDragStart = { interactionStarted() },
                                onDragEnd = { interactionEnded() },
                                onDragCancel = { interactionEnded() },
                            ) { change, drag ->
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

@Composable
private fun SmallBtn(onClick: () -> Unit, content: @Composable () -> Unit) =
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) { content() }
