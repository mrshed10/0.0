package com.athar.app.widget

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
 * In-app preview of a widget design: the same backdrop renderer as the real
 * widget plus a Compose copy of its layout, filled with real data.
 */
@Composable
fun WidgetPreview(config: WidgetConfig, data: WidgetData, wide: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = config.style
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(if (wide) 2.1f else 1f)) {
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
        is WidgetData.DateInfo -> Column(centered, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PText(d.day, text, if (small) 22.sp else 28.sp, bold = true, center = true)
            PText(d.gregorian, text.copy(alpha = 0.8f), 13.sp, center = true)
            PText(d.hijri, accent, 14.sp, bold = true, center = true)
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
