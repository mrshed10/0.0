package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The one Athar widget. Each placed instance has its own [WidgetConfig]
 * (content + style). The glass backdrop is a rendered bitmap.
 */
class AtharWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetStore(context).get(appWidgetId)
        val data = WidgetDataLoader.load(context, config)
        provideContent {
            WidgetRoot(appWidgetId, config?.style ?: WidgetStyle(), data)
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

private val TaskIdKey = ActionParameters.Key<Long>("taskId")
private val HabitIdKey = ActionParameters.Key<Long>("habitId")

/** Tick a task straight from the home screen. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val repo = (context.applicationContext as AtharApplication).container.taskRepository
        val task = repo.tasks.first().firstOrNull { it.id == id } ?: return
        repo.setDone(id, !task.done, LocalDate.now())
        AtharWidget().updateAll(context)
    }
}

/** Mark a habit done today (or undo). */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        (context.applicationContext as AtharApplication).container.habitRepository.toggle(id, LocalDate.now())
        AtharWidget().updateAll(context)
    }
}

/** One tasbih count. */
class TasbihTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        (context.applicationContext as AtharApplication).container.settings.updateTasbih { it.tapped(LocalDate.now()) }
        AtharWidget().updateAll(context)
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
            }
        }
    }
}

private fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    TextStyle(color = c(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)

@Composable
private fun Message(text: String, color: Color, action: androidx.glance.action.Action) {
    Box(GlanceModifier.fillMaxSize().clickable(action), contentAlignment = Alignment.Center) {
        Text(text, style = style(color, 14.sp, bold = true, align = TextAlign.Center))
    }
}

@Composable
private fun Header(title: String, color: Color, accent: Color, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = style(accent, 12.sp, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (trailing != null) Text(trailing, style = style(color.copy(alpha = 0.7f), 11.sp), maxLines = 1)
    }
}

@Composable
private fun PrayerContent(d: WidgetData.NextPrayer, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().clickable(openApp(context, "prayer"))) {
        Header("🕌 " + context.getString(R.string.next_prayer), text, accent, if (small) null else d.place)
        Text(d.name, style = style(text, if (small) 24.sp else 28.sp, bold = true), maxLines = 1)
        Text(d.time, style = style(accent, if (small) 14.sp else 16.sp, bold = true), maxLines = 1)
        if (!small) {
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                d.rows.forEach { row ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(row.name, style = style(if (row.isNext) accent else text.copy(alpha = 0.7f), 10.sp, bold = row.isNext, align = TextAlign.Center), maxLines = 1)
                        Text(row.time.substringBefore(' '), style = style(text, 11.sp, bold = row.isNext, align = TextAlign.Center), maxLines = 1)
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
        Text(d.title, style = style(accent, 13.sp, bold = true), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(d.value, style = style(text, if (small) 30.sp else 38.sp, bold = true), maxLines = 1)
            if (d.unit.isNotEmpty()) {
                Spacer(GlanceModifier.width(4.dp))
                Text(d.unit, style = style(text.copy(alpha = 0.75f), 13.sp), maxLines = 1)
            }
        }
        Text(d.line1, style = style(accent, 12.sp, bold = true), maxLines = 1)
        if (!small && d.line2.isNotEmpty()) Text(d.line2, style = style(text.copy(alpha = 0.7f), 11.sp), maxLines = 1)
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
            Text(context.getString(R.string.home_no_tasks), style = style(text.copy(alpha = 0.7f), 13.sp))
        }
        d.rows.take(maxRows).forEach { row ->
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to row.id))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (row.done) "☑" else "☐", style = style(if (row.done) accent else text, 17.sp, bold = true))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    row.title,
                    style = style(if (row.done) text.copy(alpha = 0.5f) else text, if (small) 13.sp else 14.sp),
                    maxLines = 1,
                )
            }
        }
        if (d.rows.size > maxRows) {
            Text("+${d.rows.size - maxRows}", style = style(accent, 11.sp, bold = true))
        }
    }
}

@Composable
private fun HabitsContent(d: WidgetData.Habits, small: Boolean, text: Color, accent: Color) {
    val context = LocalContext.current
    if (d.single) {
        val h = d.rows.first()
        Column(
            GlanceModifier.fillMaxSize().clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (h.doneToday) "✅" else h.emoji, style = style(text, if (small) 26.sp else 32.sp, align = TextAlign.Center))
            Text(h.title, style = style(text, 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
            Text("🔥 ${h.streak}", style = style(accent, 13.sp, bold = true, align = TextAlign.Center))
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
                    GlanceModifier.defaultWeight().clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to h.id))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (h.doneToday) "✅" else h.emoji, style = style(text, 22.sp, align = TextAlign.Center))
                    Text(h.title, style = style(text, 10.sp, align = TextAlign.Center), maxLines = 1)
                    Text("🔥${h.streak}", style = style(accent, 10.sp, bold = true, align = TextAlign.Center))
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
                Text(d.owedLabel, style = style(text.copy(alpha = 0.7f), 11.sp))
                Text(d.owed, style = style(Color(0xFF2EC4B6), if (small) 18.sp else 22.sp, bold = true), maxLines = 1)
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(d.oweLabel, style = style(text.copy(alpha = 0.7f), 11.sp))
                Text(d.owe, style = style(Color(0xFFF28AB2), if (small) 18.sp else 22.sp, bold = true), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TasbihContent(d: WidgetData.Tasbih, small: Boolean, text: Color, accent: Color) {
    Column(
        GlanceModifier.fillMaxSize().clickable(actionRunCallback<TasbihTapAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.dhikr, style = style(accent, 13.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        Text("${d.count}", style = style(text, if (small) 34.sp else 42.sp, bold = true, align = TextAlign.Center))
        Text("/ ${d.target} · ${d.today}", style = style(text.copy(alpha = 0.7f), 11.sp, align = TextAlign.Center), maxLines = 1)
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
        Text(d.text, style = style(text, if (small) 15.sp else 19.sp, bold = true, align = TextAlign.Center))
        Spacer(GlanceModifier.height(4.dp))
        Text(d.ref, style = style(accent, 11.sp, align = TextAlign.Center))
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
        Text(d.day, style = style(text, if (small) 22.sp else 28.sp, bold = true, align = TextAlign.Center), maxLines = 1)
        Text(d.gregorian, style = style(text.copy(alpha = 0.8f), 13.sp, align = TextAlign.Center), maxLines = 1)
        Text(d.hijri, style = style(accent, 14.sp, bold = true, align = TextAlign.Center), maxLines = 1)
    }
}
