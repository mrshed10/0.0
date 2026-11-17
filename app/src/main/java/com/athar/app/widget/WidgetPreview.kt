package com.athar.app.widget

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.R
import com.athar.app.core.fonts.FontRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val LocalPreviewFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * In-app preview of a widget design. It is rendered by the real widget code
 * (Glance → RemoteViews → views), so it looks exactly like the home-screen
 * widget. Taps go to [onTap], never to the widget's own buttons.
 * [size] = the real widget size when known (editing a placed widget).
 */
@Composable
fun WidgetPreview(
    config: WidgetConfig,
    data: WidgetData,
    wide: Boolean,
    modifier: Modifier = Modifier,
    size: DpSize? = null,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val ratio = size?.let { (it.width.value / it.height.value).coerceIn(0.5f, 4f) } ?: if (wide) 2.1f else 1f
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(ratio)) {
        val target = size ?: DpSize(maxWidth, maxHeight)
        if (LiveClockViews.isLive(data, config.style)) {
            // Clocks: the widget's own backdrop + the same live clock views, drawn directly.
            LiveClockPreview(config, data, target)
        } else {
            val views by produceState<RemoteViews?>(null, config, data, target) {
                value = if (data == WidgetData.Unconfigured) null else WidgetPreviewRenderer.render(context, config, data, target)
            }
            var failed by remember(views) { mutableStateOf(false) }
            val real = views
            if (real != null && !failed) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        runCatching { frame.addView(real.apply(frame.context, frame)) }.onFailure { failed = true }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ComposePreview(config, data, wide)
            }
        }
        // Catch all taps: the preview is not interactive.
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap?.invoke() },
        )
    }
}

/** Compose look-alike, used while the real rendering loads or if it is unavailable. */
@Composable
private fun ComposePreview(config: WidgetConfig, data: WidgetData, wide: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = config.style
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(8)
        val hPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(8)
        val backdrop by produceState<Bitmap?>(null, style, wPx, hPx) {
            value = withContext(Dispatchers.Default) { WidgetBackground.render(context, style, wPx, hPx) }
        }
        backdrop?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        val fontId = if (data is WidgetData.Clock && !ClockLayouts.supportsFont(style.fontId)) null else style.fontId
        val family = remember(fontId) { fontId?.let { FontRegistry.composeFamily(context, it) } ?: FontFamily.Default }
        val small = !wide && maxWidth < 200.dp
        CompositionLocalProvider(LocalPreviewFont provides family) {
            Box(Modifier.fillMaxSize().padding(if (small) 10.dp else 14.dp)) {
                PreviewContent(data, style, small)
            }
        }
    }
}

@Composable
private fun PText(
    text: String,
    color: Color,
    size: TextUnit,
    bold: Boolean = false,
    center: Boolean = false,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = color,
        fontSize = size,
        fontFamily = LocalPreviewFont.current,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun PreviewContent(d: WidgetData, style: WidgetStyle, small: Boolean) {
    val context = LocalContext.current
    val text = Color(style.textColor)
    val accent = Color(style.accentColor)
    val dim = text.copy(alpha = 0.7f)
    val centered = Modifier.fillMaxSize()
    when (d) {
        WidgetData.Unconfigured, WidgetData.Missing -> Box(centered, contentAlignment = Alignment.Center) {
            PText(context.getString(R.string.widget_no_items), dim, 12.sp, center = true, maxLines = 3)
        }
        is WidgetData.NextPrayer -> Column {
            PText("🕌 " + context.getString(R.string.next_prayer) + if (small) "" else " · ${d.place}", accent, 12.sp, bold = true)
            PText(d.name, text, if (small) 24.sp else 28.sp, bold = true)
            PText(d.time, accent, if (small) 14.sp else 16.sp, bold = true)
            if (!small) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    d.rows.forEach { r ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            PText(r.name, if (r.isNext) accent else dim, 10.sp, bold = r.isNext, center = true)
                            PText(r.time.substringBefore(' '), text, 11.sp, bold = r.isNext, center = true)
                        }
                    }
                }
            }
        }
        is WidgetData.Big -> Column {
            PText(d.title, accent, 13.sp, bold = true)
            Row(verticalAlignment = Alignment.Bottom) {
                PText(d.value, text, if (small) 30.sp else 38.sp, bold = true)
                if (d.unit.isNotEmpty()) PText(" " + d.unit, text.copy(alpha = 0.75f), 13.sp)
            }
            PText(d.line1, accent, 12.sp, bold = true)
            if (!small && d.line2.isNotEmpty()) PText(d.line2, dim, 11.sp)
        }
        is WidgetData.Tasks -> Column {
            Row {
                PText("✅ " + context.getString(R.string.today_tasks), accent, 12.sp, bold = true, modifier = Modifier.weight(1f))
                PText(d.summary, dim, 11.sp)
            }
            if (d.rows.isEmpty()) PText(context.getString(R.string.home_no_tasks), dim, 13.sp)
            d.rows.take(if (small) 3 else 4).forEach { r ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PText(if (r.done) "☑" else "☐", if (r.done) accent else text, 16.sp, bold = true)
                    Spacer(Modifier.width(6.dp))
                    PText(r.title, if (r.done) text.copy(alpha = 0.5f) else text, 13.sp)
                }
            }
        }
        is WidgetData.Habits -> if (d.single) {
            val h = d.rows.first()
            Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PText(if (h.doneToday) "✅" else h.emoji, text, 30.sp, center = true)
                PText(h.title, text, 13.sp, bold = true, center = true)
                PText("🔥 ${h.streak}", accent, 13.sp, bold = true, center = true)
            }
        } else {
            Column {
                PText("🔥 " + context.getString(R.string.habits), accent, 12.sp, bold = true)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    d.rows.take(if (small) 3 else 5).forEach { h ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            PText(if (h.doneToday) "✅" else h.emoji, text, 22.sp, center = true)
                            PText(h.title, text, 10.sp, center = true)
                            PText("🔥${h.streak}", accent, 10.sp, bold = true, center = true)
                        }
                    }
                }
            }
        }
        is WidgetData.Debts -> Column {
            PText("💼 " + context.getString(R.string.debts) + " · " + d.currency, accent, 12.sp, bold = true)
            Spacer(Modifier.height(6.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    PText(d.owedLabel, dim, 11.sp)
                    PText(d.owed, Color(0xFF2EC4B6), if (small) 18.sp else 22.sp, bold = true)
                }
                Column(Modifier.weight(1f)) {
                    PText(d.oweLabel, dim, 11.sp)
                    PText(d.owe, Color(0xFFF28AB2), if (small) 18.sp else 22.sp, bold = true)
                }
            }
        }
        is WidgetData.Tasbih -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PText(d.dhikr, accent, 13.sp, bold = true, center = true)
            PText("${d.count}", text, if (small) 34.sp else 42.sp, bold = true, center = true)
            PText("/ ${d.target} · ${d.today}", dim, 11.sp, center = true)
        }
        is WidgetData.Verse -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PText(d.text, text, if (small) 15.sp else 19.sp, bold = true, center = true, maxLines = 4)
            Spacer(Modifier.height(4.dp))
            PText(d.ref, accent, 11.sp, center = true)
        }
        is WidgetData.DateInfo -> when (style.variant) {
            1 -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PText(d.day, text.copy(alpha = 0.75f), 15.sp, center = true)
                PText(d.dayNum.toString(), text, if (small) 44.sp else 56.sp, bold = true, center = true)
                PText(d.month, accent, 15.sp, center = true)
            }
            2 -> Box(centered, contentAlignment = Alignment.Center) {
                PText("${d.day}، ${d.dayNum} ${d.month}، ${d.year}", text, 16.sp, bold = true, center = true)
            }
            3 -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PText("${d.day} ${d.hDay} ${d.hMonth} (${d.hMonthNum})، ${d.hYear}", text, 13.sp, bold = true, center = true)
                PText("${d.dayNum} ${d.month} (${d.monthNum})، ${d.year}", text.copy(alpha = 0.8f), 13.sp, center = true)
            }
            4 -> Row(centered, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                PText(d.dayNum.toString(), text, 26.sp, bold = true)
                Spacer(Modifier.width(6.dp))
                PText(d.monthCal, text, 24.sp)
                PText("  |  ", text.copy(alpha = 0.3f), 22.sp)
                PText(d.hDay.toString(), accent, 26.sp, bold = true)
                Spacer(Modifier.width(6.dp))
                PText(d.hMonthCal, accent, 24.sp)
            }
            else -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PText(d.day, text, if (small) 22.sp else 28.sp, bold = true, center = true)
                PText(d.gregorian, text.copy(alpha = 0.8f), 13.sp, center = true)
                PText(d.hijri, accent, 14.sp, bold = true, center = true)
            }
        }
        is WidgetData.Schedule -> Column {
            PText("🗓️ " + context.getString(R.string.widget_type_schedule), accent, 12.sp, bold = true)
            Spacer(Modifier.height(4.dp))
            d.rows.take(if (small) 3 else 4).forEach { r ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PText(if (r.salary) "💰" else "⏰", text, 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        PText(r.name, text, 12.sp, bold = true)
                        if (!small) PText(r.date, text.copy(alpha = 0.65f), 10.sp)
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (r.days == 0L) accent else accent.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        PText(r.daysText, if (r.days == 0L) Color(0xFF0B1026) else text, 11.sp, bold = true)
                    }
                }
            }
        }
        is WidgetData.BigClock -> RenderedPreview(style) { w, h, tf ->
            WidgetRenderers.bigClock(w, h, d.lines, tf ?: FontRegistry.typeface(context, "bebasneue"), style.textColor, style.accentColor)
        }
        is WidgetData.MonthGrid -> RenderedPreview(style) { w, h, tf ->
            WidgetRenderers.calendar(
                w, h, d.month, d.today, d.startSunday, d.weekdays, tf, style.textColor, style.accentColor,
                context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL,
            )
        }
        is WidgetData.Calligraphy -> RenderedPreview(style) { w, h, tf ->
            WidgetRenderers.calligraphy(
                w, h, d.main, d.number, d.sub, tf ?: FontRegistry.typeface(context, if (d.latin) "cinzel" else "arefruqaa"),
                style.textColor, style.accentColor, d.latin,
            )
        }
        is WidgetData.Dots -> Column {
            Row {
                PText(d.title, text.copy(alpha = 0.8f), 13.sp, modifier = Modifier.weight(1f))
                PText("${d.percent}%", accent, 14.sp, bold = true)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f)) {
                RenderedPreview(style) { w, h, _ -> WidgetRenderers.dots(w, h, d.filled, d.total, style.litDots(), style.dotOff) }
            }
        }
        is WidgetData.WorldClock -> WorldPreview(d, style, text, accent)
        is WidgetData.NothingStyle -> RenderedPreview(style) { w, h, tf ->
            NothingRenderer.render(w, h, d.spec, style.textColor, style.accentColor, tf, style.dotOn, style.dotOff)
        }
        is WidgetData.Custom -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = when (d.align) {
                0 -> Alignment.Start
                2 -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
            verticalArrangement = Arrangement.Center,
        ) {
            d.lines.forEach { line ->
                val base = line.color?.let { Color(it) } ?: if (line.accent) accent else text
                val family = remember(line.fontId) { line.fontId?.let { FontRegistry.composeFamily(context, it) } }
                Text(
                    line.text,
                    color = base.copy(alpha = base.alpha * line.opacity / 100f),
                    fontSize = line.sizeSp.sp,
                    fontFamily = family ?: LocalPreviewFont.current,
                    fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (line.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = if (line.underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                    textAlign = when (line.align) {
                        0 -> TextAlign.Start
                        2 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is WidgetData.WideClock -> WidePreview(style.variant == 1, text)
        is WidgetData.Clock -> if (d.analog) {
            AnalogPreview(dark = style.variant == 1, accent = accent)
        } else {
            DigitalPreview(d, style, small, text, accent)
        }
    }
}

@Composable
private fun DigitalPreview(d: WidgetData.Clock, style: WidgetStyle, small: Boolean, text: Color, accent: Color) {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PText(DateTimeFormatter.ofPattern("h:mm").format(time), text, if (small) 40.sp else 58.sp, bold = true, center = true)
        if (style.variant == 0) {
            PText(d.day, accent, 14.sp, center = true)
            if (!small) PText(d.hijri, text.copy(alpha = 0.8f), 12.sp, center = true)
        }
    }
}

/** Matches the dial/hands drawables used by the real analog widget. */
@Composable
private fun AnalogPreview(dark: Boolean, accent: Color) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    val ink = if (dark) Color(0xFF0B1026) else Color.White
    Canvas(Modifier.fillMaxSize().aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ink.copy(alpha = 0.8f), radius = r * 0.98f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.015f))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val inner = if (i % 3 == 0) 0.86f else 0.90f
            drawLine(
                if (i == 0) Color(0xFFE6C27A) else ink.copy(alpha = 0.8f),
                Offset(c.x + r * inner * sin(a).toFloat(), c.y - r * inner * cos(a).toFloat()),
                Offset(c.x + r * 0.96f * sin(a).toFloat(), c.y - r * 0.96f * cos(a).toFloat()),
                strokeWidth = if (i % 3 == 0) r * 0.04f else r * 0.02f,
                cap = StrokeCap.Round,
            )
        }
        val hourAngle = (now.hour % 12 + now.minute / 60f) * 30f
        val minuteAngle = (now.minute + now.second / 60f) * 6f
        rotate(hourAngle, c) { drawLine(ink, Offset(c.x, c.y + r * 0.08f), Offset(c.x, c.y - r * 0.48f), r * 0.07f, StrokeCap.Round) }
        rotate(minuteAngle, c) { drawLine(ink, Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.78f), r * 0.04f, StrokeCap.Round) }
        drawCircle(accent, radius = r * 0.06f, center = c)
    }
}

/** Uses the widget's own bitmap renderer so the preview matches exactly. */
@Composable
private fun RenderedPreview(style: WidgetStyle, draw: (Int, Int, android.graphics.Typeface?) -> Bitmap) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }.coerceAtLeast(8)
        val h = with(density) { maxHeight.roundToPx() }.coerceAtLeast(8)
        val bitmap by produceState<Bitmap?>(null, style, w, h) {
            value = withContext(Dispatchers.Default) { draw(w, h, FontRegistry.typeface(context, style.fontId)) }
        }
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun WorldPreview(d: WidgetData.WorldClock, style: WidgetStyle, text: Color, accent: Color) {
    val now by produceState(java.time.Instant.now()) {
        while (true) {
            value = java.time.Instant.now()
            delay(1_000)
        }
    }
    val fmt = DateTimeFormatter.ofPattern("hh:mm", java.util.Locale.ENGLISH)
    val ampm = DateTimeFormatter.ofPattern("a")
    @Composable
    fun city(c: WorldCity, modifier: Modifier) {
        val t = now.atZone(java.time.ZoneId.of(c.zone))
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            PText(c.name(d.arabic), accent, 12.sp, center = true)
            PText(fmt.format(t), text, 30.sp, bold = true, center = true)
            PText(ampm.format(t), text.copy(alpha = 0.7f), 11.sp, center = true)
        }
    }
    if (style.variant == 1) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            city(d.a, Modifier.fillMaxWidth())
            city(d.b, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            city(d.a, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(60.dp).background(text.copy(alpha = 0.2f)))
            city(d.b, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WidePreview(dark: Boolean, text: Color) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    val ink = if (dark) Color(0xFF0B1026) else Color.White
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val dx = sin(a).toFloat()
                val dy = -cos(a).toFloat()
                val tx = if (kotlin.math.abs(dx) > 1e-4f) (c.x - 4f) / kotlin.math.abs(dx) else Float.MAX_VALUE
                val ty = if (kotlin.math.abs(dy) > 1e-4f) (c.y - 4f) / kotlin.math.abs(dy) else Float.MAX_VALUE
                val t = minOf(tx, ty)
                val len = if (i % 5 == 0) 14f else 7f
                drawLine(
                    if (i == 0) Color(0xFFE6C27A) else ink.copy(alpha = 0.7f),
                    Offset(c.x + dx * (t - len), c.y + dy * (t - len)),
                    Offset(c.x + dx * t, c.y + dy * t),
                    strokeWidth = if (i % 5 == 0) 3f else 1.5f,
                    cap = StrokeCap.Round,
                )
            }
            val r = size.height * 0.4f
            rotate((now.hour % 12 + now.minute / 60f) * 30f, c) { drawLine(ink, c, Offset(c.x, c.y - r * 0.6f), r * 0.08f, StrokeCap.Round) }
            rotate((now.minute + now.second / 60f) * 6f, c) { drawLine(ink, c, Offset(c.x, c.y - r * 0.95f), r * 0.05f, StrokeCap.Round) }
            rotate(now.second * 6f, c) { drawLine(Color(0xFFE53935), Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r), 2f, StrokeCap.Round) }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            PText(DateTimeFormatter.ofPattern("h").format(now), text, 34.sp, bold = true)
            Spacer(Modifier.weight(1f))
            PText(DateTimeFormatter.ofPattern("mm").format(now), text, 34.sp, bold = true)
        }
    }
}

/** Clock preview built from the exact views the widget uses. Falls back to a drawn look-alike. */
@Composable
private fun LiveClockPreview(config: WidgetConfig, data: WidgetData, target: DpSize) {
    val context = LocalContext.current
    val style = config.style
    val density = LocalDensity.current
    val wPx = with(density) { target.width.roundToPx() }.coerceAtLeast(8)
    val hPx = with(density) { target.height.roundToPx() }.coerceAtLeast(8)
    val backdrop by produceState<Bitmap?>(null, style, wPx, hPx) {
        value = withContext(Dispatchers.Default) { WidgetBackground.render(context, style, wPx, hPx) }
    }
    val wDp = target.width.value
    val hDp = target.height.value
    val pad = LiveClockViews.paddingDp(wDp, hDp)
    var failed by remember(data, style, wDp, hDp) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        backdrop?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        if (failed) {
            Box(Modifier.fillMaxSize().padding(pad.dp)) {
                CompositionLocalProvider(LocalPreviewFont provides FontFamily.Default) { PreviewContent(data, style, wDp < 200f) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        runCatching {
                            val views = LiveClockViews.build(frame.context, data, style, wDp, hDp) ?: error("no views")
                            frame.addView(views.apply(frame.context, frame))
                        }.onFailure { failed = true }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (data is WidgetData.Clock && !data.analog && style.variant == 0 && wDp >= 170f && hDp >= 120f) {
                    Text(data.hijri, color = Color(style.textColor).copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
