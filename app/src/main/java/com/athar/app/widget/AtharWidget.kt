package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.athar.app.AtharApplication
import com.athar.app.MainActivity
import com.athar.app.R
import com.athar.app.core.data.tapped
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.fonts.TextBitmap
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The one Athar widget. Each placed instance has its own [WidgetConfig]
 * (content + style). The glass backdrop is a rendered bitmap.
 */
class AtharWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    /**
     * The composition stays alive between updates, so data is collected as a
     * flow: it reloads whenever this widget's config changes or app data is
     * refreshed (instead of being read once and going stale).
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = WidgetStore(context)
        val firstConfig = store.get(appWidgetId)
        val first = firstConfig to WidgetDataLoader.load(context, firstConfig)
        val live = combine(WidgetStore.changes, WidgetUpdater.version) { a, b -> a to b }
            .map {
                val config = store.get(appWidgetId)
                config to WidgetDataLoader.load(context, config)
            }
        provideContent {
            val state by live.collectAsState(initial = first)
            WidgetRoot(appWidgetId, state.first?.style ?: WidgetStyle(), state.second)
        }
    }
}

class AtharWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AtharWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::remove)
    }
}

/** Same widget, smaller default size in the widget picker. */
class AtharSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AtharWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::remove)
    }
}

// ---------------------------------------------------------------- UI

private fun c(argb: Int) = ColorProvider(Color(argb))
private fun c(color: Color) = ColorProvider(color)

private fun openApp(context: Context, tab: String) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

@Composable
private fun WidgetRoot(appWidgetId: Int, style: WidgetStyle, data: WidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(1)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(1)
    val backdrop = remember(style, widthPx, heightPx) { WidgetBackground.render(context, style, widthPx, heightPx) }
    val small = size.width < 170.dp || size.height < 100.dp
    val text = Color(style.textColor)
    val accent = Color(style.accentColor)

    val font = remember(style.fontId) {
        FontRegistry.typeface(context, style.fontId)?.let { it to FontRegistry.boldTypeface(context, style.fontId, it) }
    }
    val padPx = ((if (small) 10 else 14) * 2 * density).toInt()

    CompositionLocalProvider(LocalWidgetFont provides font, LocalTextWidth provides (widthPx - padPx).coerceAtLeast(40)) {
    Box(GlanceModifier.fillMaxSize()) {
        Image(
            provider = ImageProvider(backdrop),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(GlanceModifier.fillMaxSize().padding(if (small) 10.dp else 14.dp)) {
            when (data) {
                WidgetData.Unconfigured -> Message(
                    context.getString(R.string.widget_tap_to_setup),
                    text,
                    actionStartActivity(
                        Intent(context, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ),
                )
                WidgetData.Missing -> Message(context.getString(R.string.widget_item_missing), text, openApp(context, "home"))
                is WidgetData.NextPrayer -> PrayerContent(data, small, text, accent)
                is WidgetData.Big -> BigContent(data, small, text, accent)
                is WidgetData.Tasks -> TasksContent(data, small, text, accent)
                is WidgetData.Habits -> HabitsContent(data, small, text, accent)
                is WidgetData.Debts -> DebtsContent(data, small, text, accent)
                is WidgetData.Tasbih -> TasbihContent(data, small, text, accent)
                is WidgetData.Verse -> VerseContent(data, small, text, accent)
                is WidgetData.DateInfo -> DateContent(data, small, text, accent)
                is WidgetData.Schedule -> ScheduleContent(data, small, text, accent)
            }
        }
    }
    }
}

private data class WStyle(val color: Color, val size: TextUnit, val bold: Boolean, val align: TextAlign)

private fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    WStyle(color, size, bold, align)

/** Custom widget font (regular, bold); null = system font. */
private val LocalWidgetFont = compositionLocalOf<Pair<Typeface, Typeface>?> { null }

/** Width available for text, in px. */
private val LocalTextWidth = compositionLocalOf { 600 }

/**
 * Widget text. With a custom font it is drawn into a bitmap (widgets can only
 * use system fonts); otherwise it is a normal widget text view.
 */
@Composable
private fun WText(
    text: String,
    style: WStyle,
    maxLines: Int = 3,
    modifier: GlanceModifier = GlanceModifier,
    widthFraction: Float = 1f,
) {
    val font = LocalWidgetFont.current
    if (font == null) {
        Text(
            text,
            style = TextStyle(
                color = c(style.color),
                fontSize = style.size,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = style.align,
            ),
            maxLines = maxLines,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val maxWidth = (LocalTextWidth.current * widthFraction).toInt()
    val bitmap = remember(text, style, maxLines, maxWidth, font) {
        TextBitmap.render(
            context,
            text,
            if (style.bold) font.second else font.first,
            style.size.value,
            style.color.toArgb(),
            maxWidth,
            maxLines,
            center = style.align == TextAlign.Center,
        )
    }
    Box(
        modifier,
        contentAlignment = if (style.align == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
    ) {
        Image(ImageProvider(bitmap), contentDescription = text)
    }
}

@Composable
private fun Message(text: String, color: Color, action: androidx.glance.action.Action) {
    Box(GlanceModifier.fillMaxSize().clickable(action), contentAlignment = Alignment.Center) {
        WText(text, style = style(color, 14.sp, bold = true, align = TextAlign.Center))
    }
}

@Composable
private fun Header(title: String, color: Color, accent: Color, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WText(title, style = style(accent, 12.sp, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (trailing != null) WText(trailing, style = style(color.copy(alpha = 0.7f), 11.sp), maxLines = 1)
    }
}

@Composable
private fun PrayerContent(d: WidgetData.NextPrayer, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().clickable(openApp(context, "prayer"))) {
        Header("🕌 " + context.getString(R.string.next_prayer), text, accent, if (small) null else d.place)
        WText(d.name, style = style(text, if (small) 24.sp else 28.sp, bold = true), maxLines = 1)
        WText(d.time, style = style(accent, if (small) 14.sp else 16.sp, bold = true), maxLines = 1)
        if (!small) {
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                d.rows.forEach { row ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val share = 1f / d.rows.size
                        WText(row.name, style = style(if (row.isNext) accent else text.copy(alpha = 0.7f), 10.sp, bold = row.isNext, align = TextAlign.Center), maxLines = 1, widthFraction = share)
                        WText(row.time.substringBefore(' '), style = style(text, 11.sp, bold = row.isNext, align = TextAlign.Center), maxLines = 1, widthFraction = share)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigContent(d: WidgetData.Big, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().clickable(openApp(context, d.tab))) {
        WText(d.title, style = style(accent, 13.sp, bold = true), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            WText(d.value, style = style(text, if (small) 30.sp else 38.sp, bold = true), maxLines = 1)
            if (d.unit.isNotEmpty()) {
                Spacer(GlanceModifier.width(4.dp))
                WText(d.unit, style = style(text.copy(alpha = 0.75f), 13.sp), maxLines = 1)
            }
        }
        WText(d.line1, style = style(accent, 12.sp, bold = true), maxLines = 1)
        if (!small && d.line2.isNotEmpty()) WText(d.line2, style = style(text.copy(alpha = 0.7f), 11.sp), maxLines = 1)
    }
}

@Composable
private fun TasksContent(d: WidgetData.Tasks, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    val size = LocalSize.current
    val maxRows = ((size.height.value - 40) / 26).toInt().coerceIn(1, 8)
    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.fillMaxWidth().clickable(openApp(context, "tasks"))) {
            Header("✅ " + context.getString(R.string.today_tasks), text, accent, d.summary)
        }
        Spacer(GlanceModifier.height(4.dp))
        if (d.rows.isEmpty()) {
            WText(context.getString(R.string.home_no_tasks), style = style(text.copy(alpha = 0.7f), 13.sp))
        }
        d.rows.take(maxRows).forEach { row ->
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(actionSendBroadcast(WidgetActionReceiver.intent(LocalContext.current, WidgetActionReceiver.TOGGLE_TASK, row.id))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WText(if (row.done) "☑" else "☐", style = style(if (row.done) accent else text, 17.sp, bold = true))
                Spacer(GlanceModifier.width(8.dp))
                WText(
                    row.title,
                    style = style(if (row.done) text.copy(alpha = 0.5f) else text, if (small) 13.sp else 14.sp),
                    maxLines = 1,
                )
            }
        }
        if (d.rows.size > maxRows) {
            WText("+${d.rows.size - maxRows}", style = style(accent, 11.sp, bold = true))
        }
    }
}

@Composable
private fun HabitsContent(d: WidgetData.Habits, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    if (d.single) {
        val h = d.rows.first()
        Column(
            GlanceModifier.fillMaxSize().clickable(actionSendBroadcast(WidgetActionReceiver.intent(context, WidgetActionReceiver.TOGGLE_HABIT, h.id))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WText(if (h.doneToday) "✅" else h.emoji, style = style(text, if (small) 26.sp else 32.sp, align = TextAlign.Center))
            WText(h.title, style = style(text, 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            WText("🔥 ${h.streak}", style = style(accent, 13.sp, bold = true, align = TextAlign.Center))
        }
        return
    }
    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.fillMaxWidth().clickable(openApp(context, "habits"))) {
            Header("🔥 " + context.getString(R.string.habits), text, accent)
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            d.rows.take(if (small) 3 else 5).forEach { h ->
                Column(
                    GlanceModifier.defaultWeight().clickable(actionSendBroadcast(WidgetActionReceiver.intent(context, WidgetActionReceiver.TOGGLE_HABIT, h.id))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WText(if (h.doneToday) "✅" else h.emoji, style = style(text, 22.sp, align = TextAlign.Center))
                    WText(h.title, style = style(text, 10.sp, align = TextAlign.Center), maxLines = 1, widthFraction = 0.2f)
                    WText("🔥${h.streak}", style = style(accent, 10.sp, bold = true, align = TextAlign.Center))
                }
            }
        }
    }
}

@Composable
private fun DebtsContent(d: WidgetData.Debts, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().clickable(openApp(context, "debts"))) {
        Header("💼 " + context.getString(R.string.debts), text, accent, d.currency)
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                WText(d.owedLabel, style = style(text.copy(alpha = 0.7f), 11.sp))
                WText(d.owed, style = style(Color(0xFF2EC4B6), if (small) 18.sp else 22.sp, bold = true), maxLines = 1)
            }
            Column(GlanceModifier.defaultWeight()) {
                WText(d.oweLabel, style = style(text.copy(alpha = 0.7f), 11.sp))
                WText(d.owe, style = style(Color(0xFFF28AB2), if (small) 18.sp else 22.sp, bold = true), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TasbihContent(d: WidgetData.Tasbih, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(actionSendBroadcast(WidgetActionReceiver.intent(context, WidgetActionReceiver.TASBIH, 0))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WText(d.dhikr, style = style(accent, 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        WText("${d.count}", style = style(text, if (small) 34.sp else 42.sp, bold = true, align = TextAlign.Center))
        WText("/ ${d.target} · ${d.today}", style = style(text.copy(alpha = 0.7f), 11.sp, align = TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun VerseContent(d: WidgetData.Verse, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(openApp(context, "home")),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WText(d.text, style = style(text, if (small) 15.sp else 19.sp, bold = true, align = TextAlign.Center))
        Spacer(GlanceModifier.height(4.dp))
        WText(d.ref, style = style(accent, 11.sp, align = TextAlign.Center))
    }
}

@Composable
private fun DateContent(d: WidgetData.DateInfo, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(openApp(context, "home")),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WText(d.day, style = style(text, if (small) 22.sp else 28.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        WText(d.gregorian, style = style(text.copy(alpha = 0.8f), 13.sp, align = TextAlign.Center), maxLines = 1)
        WText(d.hijri, style = style(accent, 14.sp, bold = true, align = TextAlign.Center), maxLines = 1)
    }
}

/** All upcoming salaries and appointments with the days left for each. */
@Composable
private fun ScheduleContent(d: WidgetData.Schedule, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    val size = LocalSize.current
    val rowHeight = if (small) 30 else 34
    val maxRows = ((size.height.value - 34) / rowHeight).toInt().coerceIn(1, 10)
    Column(GlanceModifier.fillMaxSize().clickable(openApp(context, "salaries"))) {
        Header("🗓️ " + context.getString(R.string.widget_type_schedule), text, accent)
        Spacer(GlanceModifier.height(4.dp))
        d.rows.take(maxRows).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                WText(if (row.salary) "💰" else "⏰", style = style(text, 13.sp))
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    WText(row.name, style = style(text, if (small) 12.sp else 13.sp, bold = true), maxLines = 1, widthFraction = 0.6f)
                    if (!small) WText(row.date, style = style(text.copy(alpha = 0.65f), 10.sp), maxLines = 1, widthFraction = 0.6f)
                }
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    GlanceModifier
                        .background(ImageProvider(pill(context, if (row.days == 0L) accent else accent.copy(alpha = 0.25f))))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WText(row.daysText, style = style(if (row.days == 0L) Color(0xFF0B1026) else text, 11.sp, bold = true), maxLines = 1, widthFraction = 0.3f)
                }
            }
        }
        if (d.rows.size > maxRows) WText("+${d.rows.size - maxRows}", style = style(accent, 11.sp, bold = true))
    }
}

/** Small rounded pill bitmap used behind the "days left" label. */
private fun pill(context: Context, color: Color): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (64 * density).toInt()
    val h = (22 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    android.graphics.Canvas(bitmap).drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, paint)
    return bitmap
}
