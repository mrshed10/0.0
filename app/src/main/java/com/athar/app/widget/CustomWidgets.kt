package com.athar.app.widget

import android.content.Context
import androidx.annotation.StringRes
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.data.todayCount
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.prayer.PrayerSchedule
import com.athar.app.feature.prayer.SpiritualContent
import com.athar.app.feature.prayer.dhikr
import com.athar.app.feature.salary.SalarySchedule
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Building blocks for a widget the user designs. */
enum class ElementKind(@StringRes val label: Int, val ticks: Boolean = false) {
    Text(R.string.el_text),
    Time(R.string.el_time, ticks = true),
    DayName(R.string.el_day),
    GregorianDate(R.string.el_gregorian),
    HijriDate(R.string.el_hijri),
    NextPrayer(R.string.el_next_prayer),
    PrayerCountdown(R.string.el_prayer_countdown, ticks = true),
    Counter(R.string.el_counter),
    Salary(R.string.el_salary),
    Tasbih(R.string.el_tasbih),
    Verse(R.string.el_verse),
    GregorianNumeric(R.string.el_gregorian_numeric),
    HijriNumeric(R.string.el_hijri_numeric),
    MonthName(R.string.el_month),
    DaysLeftInMonth(R.string.el_days_left_month),
    YearProgress(R.string.el_year_progress),
    IqamaCountdown(R.string.el_iqama_countdown, ticks = true),
    TasksToday(R.string.el_tasks_today),
    HabitsToday(R.string.el_habits_today),
    Debts(R.string.el_debts),
}

data class CustomElement(
    val kind: ElementKind,
    val text: String = "",
    val sizeSp: Int = 16,
    /** Explicit colour (ARGB); null = widget text colour (or accent if [accent]). */
    val color: Int? = null,
    val accent: Boolean = false,
    val bold: Boolean = false,
    /** Draw in dot-matrix (Nothing style) when the text allows it. */
    val dots: Boolean = false,
    /** Record id for Counter (‑1 = nearest). */
    val ref: Long = -1,
    /** This element's own font; null = the widget's font. */
    val fontId: String? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** 0 start, 1 centre, 2 end; null = the widget's alignment. */
    val align: Int? = null,
    /** Opacity of this element, 10–100 %. */
    val opacity: Int = 100,
)

/** [align]: 0 start, 1 centre, 2 end. */
data class CustomSpec(val elements: List<CustomElement>, val align: Int = 1) {
    companion object {
        fun starter() = CustomSpec(
            listOf(
                CustomElement(ElementKind.DayName, sizeSp = 14, accent = true),
                CustomElement(ElementKind.Time, sizeSp = 40, bold = true),
                CustomElement(ElementKind.HijriDate, sizeSp = 13),
            ),
        )
    }
}

object CustomSpecJson {
    fun encode(spec: CustomSpec): String = JSONObject()
        .put("align", spec.align)
        .put("elements", JSONArray().apply {
            spec.elements.forEach { e ->
                put(
                    JSONObject().put("kind", e.kind.name).put("text", e.text).put("size", e.sizeSp)
                        .put("color", e.color ?: JSONObject.NULL).put("accent", e.accent).put("bold", e.bold)
                        .put("dots", e.dots).put("ref", e.ref)
                        .put("font", e.fontId ?: JSONObject.NULL).put("italic", e.italic).put("underline", e.underline)
                        .put("align", e.align ?: JSONObject.NULL).put("opacity", e.opacity),
                )
            }
        })
        .toString()

    fun decode(raw: String?): CustomSpec = runCatching {
        val o = JSONObject(raw ?: return CustomSpec.starter())
        val a = o.getJSONArray("elements")
        CustomSpec(
            elements = (0 until a.length()).mapNotNull { i ->
                val e = a.getJSONObject(i)
                val kind = ElementKind.entries.firstOrNull { it.name == e.optString("kind") } ?: return@mapNotNull null
                CustomElement(
                    kind = kind,
                    text = e.optString("text", ""),
                    sizeSp = e.optInt("size", 16).coerceIn(8, 96),
                    color = if (e.isNull("color")) null else e.optInt("color"),
                    accent = e.optBoolean("accent", false),
                    bold = e.optBoolean("bold", false),
                    dots = e.optBoolean("dots", false),
                    ref = e.optLong("ref", -1),
                    fontId = if (!e.has("font") || e.isNull("font")) null else e.optString("font"),
                    italic = e.optBoolean("italic", false),
                    underline = e.optBoolean("underline", false),
                    align = if (!e.has("align") || e.isNull("align")) null else e.optInt("align").coerceIn(0, 2),
                    opacity = e.optInt("opacity", 100).coerceIn(10, 100),
                )
            }.take(MAX_ELEMENTS),
            align = o.optInt("align", 1).coerceIn(0, 2),
        )
    }.getOrDefault(CustomSpec.starter())

    const val MAX_ELEMENTS = 10
}

object CustomWidgets {
    fun needsTick(spec: CustomSpec) = spec.elements.any { it.kind.ticks }

    /** Turns each element into its current text. */
    suspend fun resolve(context: Context, spec: CustomSpec, now: LocalDateTime): WidgetData.Custom {
        val c = (context.applicationContext as AtharApplication).container
        val locale = context.resources.configuration.locales[0]
        val today = now.toLocalDate()
        val res = context.resources
        val use24 = android.text.format.DateFormat.is24HourFormat(context)
        val lines = spec.elements.map { e ->
            val text = runCatching {
                when (e.kind) {
                    ElementKind.Text -> e.text
                    ElementKind.Time -> DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "hh:mm", Locale.ENGLISH).format(now)
                    ElementKind.DayName -> today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    ElementKind.GregorianDate -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(today)
                    ElementKind.HijriDate -> {
                        val h = HijriCalendar.fromGregorian(today)
                        "${h.day} ${res.getStringArray(R.array.hijri_months)[h.month - 1]} ${h.year} ${res.getString(R.string.hijri_suffix)}"
                    }
                    ElementKind.NextPrayer, ElementKind.PrayerCountdown -> {
                        val prefs = c.settings.prayer.value
                        val next = PrayerSchedule.next(now, prefs.timesFor(today), prefs.timesFor(today.plusDays(1)))
                        if (e.kind == ElementKind.NextPrayer) {
                            res.getString(next.prayer.label) + " · " + DateTimeFormatter.ofPattern("h:mm a", locale).format(next.at)
                        } else {
                            val left = java.time.Duration.between(now, next.at).toMinutes().coerceAtLeast(0)
                            String.format(Locale.ENGLISH, "%02d:%02d", left / 60, left % 60)
                        }
                    }
                    ElementKind.Counter -> {
                        val counters = c.counterRepository.counters.first()
                        val counter = counters.firstOrNull { it.id == e.ref } ?: CounterMath.sort(counters, today).firstOrNull()
                        counter?.let { "${it.title}: ${CounterMath.read(it, today).days} ${res.getString(R.string.unit_days)}" } ?: "—"
                    }
                    ElementKind.Salary -> {
                        val weekend = c.settings.general.value.country.weekend
                        c.plannerRepository.salaries.first()
                            .map { it to SalarySchedule.next(it, today, weekend) }
                            .minByOrNull { it.second }
                            ?.let { (s, d) -> "${s.name}: ${ChronoUnit.DAYS.between(today, d)} ${res.getString(R.string.unit_days)}" } ?: "—"
                    }
                    ElementKind.Tasbih -> {
                        val t = c.settings.tasbih.value
                        val d = t.dhikr()
                        "${t.count % d.count.coerceAtLeast(1)} / ${d.count} · ${t.todayCount(today)}"
                    }
                    ElementKind.Verse -> "﴿ ${SpiritualContent.verseFor(today).ar} ﴾"
                    ElementKind.GregorianNumeric -> DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH).format(today)
                    ElementKind.HijriNumeric -> {
                        val h = HijriCalendar.fromGregorian(today)
                        String.format(Locale.ENGLISH, "%02d/%02d/%d", h.day, h.month, h.year) + " " + res.getString(R.string.hijri_suffix)
                    }
                    ElementKind.MonthName -> today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    ElementKind.DaysLeftInMonth -> {
                        val left = today.lengthOfMonth() - today.dayOfMonth
                        res.getString(R.string.el_days_left_value, left.toString())
                    }
                    ElementKind.YearProgress -> {
                        val p = (today.dayOfYear - 1) * 100 / today.lengthOfYear()
                        "${today.year} · $p%"
                    }
                    ElementKind.IqamaCountdown -> {
                        val prefs = c.settings.prayer.value
                        val times = prefs.timesFor(today)
                        val prev = PrayerSchedule.previous(now, times, prefs.timesFor(today.minusDays(1)))
                        val next = PrayerSchedule.next(now, times, prefs.timesFor(today.plusDays(1)))
                        val current = com.athar.app.feature.prayer.IqamaMath.iqamaAt(prev, prefs.alert(prev.prayer))?.takeIf { now.isBefore(it) }
                        val (prayer, at) = if (current != null) prev.prayer to current else next.prayer to com.athar.app.feature.prayer.IqamaMath.iqamaAt(next, prefs.alert(next.prayer))
                        if (at == null) {
                            "—"
                        } else {
                            val left = java.time.Duration.between(now, at).toMinutes().coerceAtLeast(0)
                            res.getString(R.string.el_iqama_value, res.getString(prayer.label), String.format(Locale.ENGLISH, "%02d:%02d", left / 60, left % 60))
                        }
                    }
                    ElementKind.TasksToday -> {
                        val tasks = com.athar.app.feature.tasks.TaskGrouping.forToday(c.taskRepository.tasks.first(), today)
                        res.getString(R.string.tasks_done, tasks.count { it.done }.toString(), tasks.size.toString())
                    }
                    ElementKind.HabitsToday -> {
                        val days = c.habitRepository.doneDays.first()
                        val habits = c.habitRepository.habits.first()
                        val done = habits.count { com.athar.app.feature.habits.StreakMath.summarize(it, days[it.id].orEmpty(), today).doneToday }
                        res.getString(R.string.el_habits_value, done.toString(), habits.size.toString())
                    }
                    ElementKind.Debts -> {
                        val totals = com.athar.app.feature.debts.DebtMath.totals(
                            com.athar.app.feature.debts.DebtMath.balances(c.debtRepository.people.first(), c.debtRepository.entries.first()),
                        )
                        res.getString(R.string.debts_owed_to_me) + " " + com.athar.app.core.format.Money.format(totals.owedToMeMinor)
                    }
                }
            }.getOrDefault("—")
            CustomLine(
                text, e.sizeSp, e.color, e.accent, e.bold, e.dots,
                fontId = e.fontId, italic = e.italic, underline = e.underline,
                align = e.align ?: spec.align, opacity = e.opacity,
            )
        }
        return WidgetData.Custom(lines, spec.align)
    }
}

/** True if this widget must be redrawn every minute. */
fun WidgetConfig.needsMinuteTick(): Boolean =
    content.type.minuteClock ||
        (content.type == WidgetType.NextPrayer && PrayerPulse.isAnimatedVariant(style.variant)) ||
        (content.type == WidgetType.Custom && CustomWidgets.needsTick(CustomSpecJson.decode(content.extra)))
