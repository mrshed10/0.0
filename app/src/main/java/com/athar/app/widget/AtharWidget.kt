package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.lazy.LazyColumn
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
        val live = combine(WidgetStore.changes, WidgetUpdater.version, WidgetUpdater.tick) { a, b, t ->
            // Clock ticks only matter to minute clocks.
            Triple(a, b, if (store.get(appWidgetId)?.content?.type?.minuteClock == true) t else 0L)
        }
            .distinctUntilChanged()
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
internal fun WidgetRoot(appWidgetId: Int, style: WidgetStyle, data: WidgetData) {
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

    val contentH = (heightPx - padPx).coerceAtLeast(40)
    CompositionLocalProvider(
        LocalContentHeight provides contentH,
        LocalWidgetFont provides font,
        LocalTextWidth provides (widthPx - padPx).coerceAtLeast(40),
        LocalClick provides style.click,
    ) {
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
                    // Setting up always opens the editor, whatever the tap behaviour.
                    GlanceModifier.clickable(
                        actionStartActivity(
                            Intent(context, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ),
                    ),
                )
                WidgetData.Missing -> Message(context.getString(R.string.widget_item_missing), text, GlanceModifier.tapOpen("home"))
                is WidgetData.NextPrayer -> PrayerContent(data, small, text, accent)
                is WidgetData.Big -> BigContent(data, small, text, accent)
                is WidgetData.Tasks -> TasksContent(data, small, text, accent)
                is WidgetData.Habits -> HabitsContent(data, small, text, accent)
                is WidgetData.Debts -> DebtsContent(data, small, text, accent)
                is WidgetData.Tasbih -> TasbihContent(data, small, text, accent)
                is WidgetData.Verse -> VerseContent(data, small, text, accent)
                is WidgetData.DateInfo -> DateContent(data, style, small, text, accent)
                is WidgetData.Schedule -> ScheduleContent(data, small, text, accent)
                is WidgetData.Clock -> ClockContent(data, style, small, text)
                is WidgetData.WideClock -> WideClockContent(style)
                is WidgetData.WorldClock -> WorldClockContent(data, style)
                is WidgetData.BigClock -> BitmapContent("home") { w, h ->
                    WidgetRenderers.bigClock(w, h, data.lines, font?.first ?: FontRegistry.typeface(context, "bebasneue"), style.textColor, style.accentColor)
                }
                is WidgetData.Dots -> DotsContent(data, style, text, accent)
                is WidgetData.MonthGrid -> BitmapContent("dates") { w, h ->
                    WidgetRenderers.calendar(
                        w, h, data.month, data.today, data.startSunday, data.weekdays, font?.first,
                        style.textColor, style.accentColor, context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL,
                    )
                }
                is WidgetData.Calligraphy -> BitmapContent("home") { w, h ->
                    val tf = font?.first ?: FontRegistry.typeface(context, if (data.latin) "cinzel" else "arefruqaa")
                    WidgetRenderers.calligraphy(w, h, data.main, data.number, data.sub, tf, style.textColor, style.accentColor, data.latin)
                }
            }
        }
    }
    }
}

/** The widget's tap behaviour (none / quick action / open app). */
private val LocalClick = compositionLocalOf { ClickBehavior.Quick }

/**
 * Tapping a non-button area: opens the app only in [ClickBehavior.OpenApp];
 * in [ClickBehavior.Quick] it just refreshes the data; [ClickBehavior.None] ignores it.
 */
@Composable
private fun GlanceModifier.tapOpen(tab: String): GlanceModifier {
    val context = LocalContext.current
    return when (LocalClick.current) {
        ClickBehavior.None -> this
        ClickBehavior.Quick -> clickable(actionSendBroadcast(WidgetActionReceiver.intent(context, WidgetActionReceiver.REFRESH, 0)))
        ClickBehavior.OpenApp -> clickable(openApp(context, tab))
    }
}

/** A button (tick a task, mark a habit, count): runs in place unless taps are off. */
@Composable
private fun GlanceModifier.tapAction(intent: Intent): GlanceModifier =
    if (LocalClick.current == ClickBehavior.None) this else clickable(actionSendBroadcast(intent))

private data class WStyle(val color: Color, val size: TextUnit, val bold: Boolean, val align: TextAlign)

private fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    WStyle(color, size, bold, align)

/** Custom widget font (regular, bold); null = system font. */
private val LocalWidgetFont = compositionLocalOf<Pair<Typeface, Typeface>?> { null }

/** Width available for text, in px. */
private val LocalTextWidth = compositionLocalOf { 600 }

/** Height available for content, in px. */
private val LocalContentHeight = compositionLocalOf { 300 }

/** Content drawn as one bitmap sized to the widget's content area. */
@Composable
private fun BitmapContent(tab: String, draw: (Int, Int) -> android.graphics.Bitmap) {
    val w = LocalTextWidth.current
    val h = LocalContentHeight.current
    val bitmap = remember(w, h, draw) { draw(w, h) }
    Box(GlanceModifier.fillMaxSize().tapOpen(tab), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

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
private fun Message(text: String, color: Color, tap: GlanceModifier) {
    Box(GlanceModifier.fillMaxSize().then(tap), contentAlignment = Alignment.Center) {
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
    Column(GlanceModifier.fillMaxSize().tapOpen("prayer")) {
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
    Column(GlanceModifier.fillMaxSize().tapOpen(d.tab)) {
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
        Box(GlanceModifier.fillMaxWidth().tapOpen("tasks")) {
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
                    .tapAction(WidgetActionReceiver.intent(LocalContext.current, WidgetActionReceiver.TOGGLE_TASK, row.id)),
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
            GlanceModifier.fillMaxSize().tapAction(WidgetActionReceiver.intent(context, WidgetActionReceiver.TOGGLE_HABIT, h.id)),
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
        Box(GlanceModifier.fillMaxWidth().tapOpen("habits")) {
            Header("🔥 " + context.getString(R.string.habits), text, accent)
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            d.rows.take(if (small) 3 else 5).forEach { h ->
                Column(
                    GlanceModifier.defaultWeight().tapAction(WidgetActionReceiver.intent(context, WidgetActionReceiver.TOGGLE_HABIT, h.id)),
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
    Column(GlanceModifier.fillMaxSize().tapOpen("debts")) {
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
        GlanceModifier.fillMaxSize().tapAction(WidgetActionReceiver.intent(context, WidgetActionReceiver.TASBIH, 0)),
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
        GlanceModifier.fillMaxSize().tapOpen("home"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WText(d.text, style = style(text, if (small) 15.sp else 19.sp, bold = true, align = TextAlign.Center))
        Spacer(GlanceModifier.height(4.dp))
        WText(d.ref, style = style(accent, 11.sp, align = TextAlign.Center))
    }
}

@Composable
private fun DateContent(d: WidgetData.DateInfo, ws: WidgetStyle, small: Boolean, text: Color, accent: Color) {
    val center = GlanceModifier.fillMaxSize().tapOpen("home")
    when (ws.variant) {
        // Simple square: day / big number / month
        1 -> Column(center, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WText(d.day, style = style(text.copy(alpha = 0.75f), 15.sp, align = TextAlign.Center), maxLines = 1)
            WText(d.dayNum.toString(), style = style(text, if (small) 44.sp else 56.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            WText(d.month, style = style(accent, 15.sp, align = TextAlign.Center), maxLines = 1)
        }
        // One-line strip: "Saturday, 15 March, 2026"
        2 -> Box(center, contentAlignment = Alignment.Center) {
            WText("${d.day}، ${d.dayNum} ${d.month}، ${d.year}", style = style(text, 16.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        }
        // Hijri and Gregorian lines with month numbers
        3 -> Column(center, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WText("${d.day} ${d.hDay} ${d.hMonth} (${d.hMonthNum})، ${d.hYear}", style = style(text, 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            WText("${d.dayNum} ${d.month} (${d.monthNum})، ${d.year}", style = style(text.copy(alpha = 0.8f), 13.sp, align = TextAlign.Center), maxLines = 1)
        }
        // Dual calendar: "19 March | 10 Sha'ban" with calligraphy month names
        4 -> Row(center, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            WText(d.dayNum.toString(), style = style(text, 26.sp, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            WText(d.monthCal, style = style(text, 24.sp), maxLines = 1, widthFraction = 0.35f)
            Spacer(GlanceModifier.width(10.dp))
            WText("|", style = style(text.copy(alpha = 0.3f), 22.sp), maxLines = 1)
            Spacer(GlanceModifier.width(10.dp))
            WText(d.hDay.toString(), style = style(accent, 26.sp, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            WText(d.hMonthCal, style = style(accent, 24.sp), maxLines = 1, widthFraction = 0.35f)
        }
        else -> Column(center, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WText(d.day, style = style(text, if (small) 22.sp else 28.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            WText(d.gregorian, style = style(text.copy(alpha = 0.8f), 13.sp, align = TextAlign.Center), maxLines = 1)
            WText(d.hijri, style = style(accent, 14.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        }
    }
}

@Composable
private fun DotsContent(d: WidgetData.Dots, ws: WidgetStyle, text: Color, accent: Color) {
    val w = LocalTextWidth.current
    val density = LocalContext.current.resources.displayMetrics.density
    val h = (LocalContentHeight.current - 26 * density).toInt().coerceAtLeast(20)
    val dots = remember(d, w, h, ws.textColor) { WidgetRenderers.dots(w, h, d.filled, d.total, ws.textColor) }
    Column(GlanceModifier.fillMaxSize().tapOpen("home")) {
        Row(GlanceModifier.fillMaxWidth()) {
            WText(d.title, style = style(text.copy(alpha = 0.8f), 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            WText("${d.percent}%", style = style(accent, 14.sp, bold = true), maxLines = 1)
        }
        Spacer(GlanceModifier.height(6.dp))
        Image(ImageProvider(dots), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** Wide analog: tick border, hour/minute digits on the sides, live hands (second hand on Android 12+). */
@Composable
private fun WideClockContent(ws: WidgetStyle) {
    val context = LocalContext.current
    val views = remember(ws.variant, ws.textColor) {
        RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_wide_clock_dark else R.layout.widget_wide_clock_light).apply {
            setTextColor(R.id.wide_hour, ws.textColor)
            setTextColor(R.id.wide_minute, ws.textColor)
        }
    }
    Box(GlanceModifier.fillMaxSize().tapOpen("home")) {
        AndroidRemoteViews(views, GlanceModifier.fillMaxSize())
    }
}

/** Two cities, each a live TextClock in its own time zone. */
@Composable
private fun WorldClockContent(d: WidgetData.WorldClock, ws: WidgetStyle) {
    val context = LocalContext.current
    val views = remember(d, ws.variant, ws.textColor, ws.accentColor) {
        RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_world_clock_stacked else R.layout.widget_world_clock).apply {
            listOf(
                Triple(R.id.city1_name, R.id.city1_time, R.id.city1_ampm) to d.a,
                Triple(R.id.city2_name, R.id.city2_time, R.id.city2_ampm) to d.b,
            ).forEach { (ids, city) ->
                setTextViewText(ids.first, city.name(d.arabic))
                setTextColor(ids.first, ws.accentColor)
                setTextColor(ids.second, ws.textColor)
                setTextColor(ids.third, ws.textColor)
                setString(ids.second, "setTimeZone", city.zone)
                setString(ids.third, "setTimeZone", city.zone)
            }
        }
    }
    Box(GlanceModifier.fillMaxSize().tapOpen("home")) {
        AndroidRemoteViews(views, GlanceModifier.fillMaxSize())
    }
}

/** All upcoming salaries and appointments with the days left for each; the list scrolls. */
@Composable
private fun ScheduleContent(d: WidgetData.Schedule, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.fillMaxWidth().tapOpen("salaries")) {
            Header("🗓️ " + context.getString(R.string.widget_type_schedule), text, accent, d.rows.size.toString())
        }
        Spacer(GlanceModifier.height(4.dp))
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(d.rows.size, itemId = { it.toLong() }) { index ->
                val row = d.rows[index]
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).tapOpen("salaries"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        WText(
                            row.daysText,
                            style = style(if (row.days == 0L) Color(0xFF0B1026) else text, 11.sp, bold = true),
                            maxLines = 1,
                            widthFraction = 0.3f,
                        )
                    }
                }
            }
        }
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

/** Live clock via the launcher's own TextClock/AnalogClock (no app wake-ups). */
@Composable
private fun ClockContent(d: WidgetData.Clock, ws: WidgetStyle, small: Boolean, text: Color) {
    val context = LocalContext.current
    val views = remember(d.analog, ws.fontId, ws.variant, ws.textColor, ws.accentColor, small) {
        if (d.analog) {
            RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_analog_dark else R.layout.widget_analog_light)
        } else {
            RemoteViews(context.packageName, ClockLayouts.digital(ws.fontId)).apply {
                setTextColor(R.id.clock_time, ws.textColor)
                setTextColor(R.id.clock_date, ws.accentColor)
                setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, if (small) 40f else 58f)
                if (ws.variant == 1) setViewVisibility(R.id.clock_date, View.GONE)
            }
        }
    }
    Column(
        GlanceModifier.fillMaxSize().tapOpen("home"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidRemoteViews(views, GlanceModifier.defaultWeight().fillMaxWidth())
        if (!d.analog && ws.variant == 0 && !small) {
            WText(d.hijri, style = style(text.copy(alpha = 0.8f), 12.sp, align = TextAlign.Center), maxLines = 1)
        }
    }
}
