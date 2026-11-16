package com.athar.app.widget

import android.content.Context
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.data.todayCount
import com.athar.app.core.format.Money
import com.athar.app.core.region.Cities
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.counters.CounterPhase
import com.athar.app.feature.debts.DebtMath
import com.athar.app.feature.habits.HabitIcon
import com.athar.app.feature.habits.StreakMath
import com.athar.app.feature.prayer.PrayerSchedule
import com.athar.app.feature.prayer.SpiritualContent
import com.athar.app.feature.prayer.dhikr
import com.athar.app.feature.reminders.ReminderMath
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.tasks.TaskGrouping
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/** Ready-to-draw text for each widget type (formatted outside composition). */
sealed interface WidgetData {
    data object Unconfigured : WidgetData
    data object Missing : WidgetData
    data class PrayerRow(val name: String, val time: String, val isNext: Boolean)
    data class NextPrayer(
        val name: String,
        val time: String,
        val place: String,
        val rows: List<PrayerRow>,
        /** Wall-clock time of the next prayer (epoch millis), for live countdowns. */
        val atMillis: Long = 0,
        /** Share of the time since the previous prayer that has passed (0..1), in 1/120 steps. */
        val progress: Float = 0f,
    ) : WidgetData
    data class Big(val title: String, val value: String, val unit: String, val line1: String, val line2: String, val tab: String) : WidgetData
    data class TaskRow(val id: Long, val title: String, val done: Boolean)
    data class Tasks(val rows: List<TaskRow>, val summary: String) : WidgetData
    data class HabitRow(val id: Long, val title: String, val emoji: String, val streak: Int, val doneToday: Boolean)
    data class Habits(val rows: List<HabitRow>, val single: Boolean) : WidgetData
    data class Debts(val owed: String, val owe: String, val owedLabel: String, val oweLabel: String, val currency: String) : WidgetData
    data class Tasbih(val count: Int, val target: Int, val dhikr: String, val today: String) : WidgetData
    data class Verse(val text: String, val ref: String) : WidgetData
    data class DateInfo(
        val day: String,
        val gregorian: String,
        val hijri: String,
        val dayNum: Int = 0,
        val month: String = "",
        val monthNum: Int = 0,
        val year: Int = 0,
        val hDay: Int = 0,
        val hMonth: String = "",
        val hMonthNum: Int = 0,
        val hYear: Int = 0,
        /** Month names with diacritics, for calligraphy. */
        val monthCal: String = "",
        val hMonthCal: String = "",
    ) : WidgetData
    data class ScheduleRow(val name: String, val date: String, val days: Long, val daysText: String, val salary: Boolean)
    data class Schedule(val rows: List<ScheduleRow>) : WidgetData
    /** Live clock; [hijri] and [day] are drawn under digital clocks. */
    data class Clock(val analog: Boolean, val hijri: String, val day: String) : WidgetData
    data object WideClock : WidgetData
    data class WorldClock(val a: WorldCity, val b: WorldCity, val arabic: Boolean) : WidgetData
    /** Bitmap clock; [lines] are "12:34" or ["12", "34"]. */
    data class BigClock(val lines: List<String>) : WidgetData
    data class Dots(val title: String, val percent: Int, val filled: Int, val total: Int) : WidgetData
    data class MonthGrid(val month: java.time.YearMonth, val today: LocalDate, val startSunday: Boolean, val weekdays: List<String>) : WidgetData
    data class Calligraphy(val main: String, val number: String?, val sub: String?, val latin: Boolean) : WidgetData
    data class NothingStyle(val spec: NothingSpec) : WidgetData
    data class Custom(val lines: List<CustomLine>, val align: Int) : WidgetData
}

/** One resolved line of a custom widget. */
data class CustomLine(val text: String, val sizeSp: Int, val color: Int?, val accent: Boolean, val bold: Boolean, val dots: Boolean)

object WidgetDataLoader {
    /** Never throws: a failure shows "item missing" instead of a broken widget. */
    suspend fun load(context: Context, config: WidgetConfig?): WidgetData {
        if (config == null) return WidgetData.Unconfigured
        return runCatching { loadUnsafe(context, config) }.getOrDefault(WidgetData.Missing)
    }

    private suspend fun loadUnsafe(context: Context, config: WidgetConfig): WidgetData {
        val c = (context.applicationContext as AtharApplication).container
        val locale = context.resources.configuration.locales[0]
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val res = context.resources
        fun s(id: Int, vararg args: Any) = res.getString(id, *args)
        val numeric = config.style.dateStyle == DateStyle.Numeric
        fun hijri(date: LocalDate): String {
            val h = HijriCalendar.fromGregorian(date)
            return if (numeric) {
                String.format(java.util.Locale.ENGLISH, "%d/%02d/%02d", h.year, h.month, h.day)
            } else {
                "${h.day} ${res.getStringArray(R.array.hijri_months)[h.month - 1]} ${h.year}"
            }
        }
        fun greg(date: LocalDate, pattern: String = "EEE d MMM"): String =
            if (numeric) {
                DateTimeFormatter.ofPattern("yyyy/MM/dd", java.util.Locale.ENGLISH).format(date)
            } else {
                DateTimeFormatter.ofPattern(pattern, locale).format(date)
            }
        fun daysText(days: Long) = when (days) {
            0L -> s(R.string.today)
            1L -> s(R.string.tomorrow)
            else -> "$days ${s(R.string.unit_days)}"
        }
        fun clock(t: java.time.LocalTime) = DateTimeFormatter.ofPattern("h:mm a", locale).format(t)

        return when (config.content.type) {
            WidgetType.NextPrayer -> {
                val prefs = c.settings.prayer.value
                val times = prefs.timesFor(today)
                val next = PrayerSchedule.next(now, times, prefs.timesFor(today.plusDays(1)))
                val previous = PrayerSchedule.previous(now, times, prefs.timesFor(today.minusDays(1)))
                WidgetData.NextPrayer(
                    name = s(next.prayer.label),
                    time = clock(next.at.toLocalTime()),
                    place = Cities.byId(prefs.cityId)?.name(locale) ?: s(R.string.my_location),
                    rows = times.map {
                        WidgetData.PrayerRow(s(it.prayer.label), clock(it.time), it.prayer == next.prayer)
                    },
                    atMillis = next.at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    // Quantized so a minute tick only redraws when a dot actually changes.
                    progress = (PrayerSchedule.progress(now, previous.at, next.at) * 120).toInt() / 120f,
                )
            }
            WidgetType.Counter -> {
                val counter = c.counterRepository.counters.first().firstOrNull { it.id == config.content.itemId }
                    ?: return WidgetData.Missing
                val reading = CounterMath.read(counter, today)
                val label = s(
                    when (reading.phase) {
                        CounterPhase.Remaining -> R.string.counter_until
                        CounterPhase.Passed -> R.string.counter_passed
                        CounterPhase.Since -> R.string.counter_since
                        CounterPhase.StartsIn -> R.string.counter_starts_in
                    },
                )
                WidgetData.Big(
                    title = counter.title,
                    value = if (reading.days == 0L) s(R.string.today) else reading.days.toString(),
                    unit = if (reading.days == 0L) "" else s(R.string.unit_days),
                    line1 = label,
                    line2 = greg(counter.date, "d MMM yyyy") + " · " + hijri(counter.date),
                    tab = "dates",
                )
            }
            WidgetType.Salary -> {
                val weekend = c.settings.general.value.country.weekend
                val sources = c.plannerRepository.salaries.first()
                val pick = if (config.content.itemId >= 0) sources.filter { it.id == config.content.itemId } else sources
                val next = pick.map { it to SalarySchedule.next(it, today, weekend) }.minByOrNull { it.second }
                    ?: return WidgetData.Missing
                val days = ChronoUnit.DAYS.between(today, next.second)
                WidgetData.Big(
                    title = next.first.name,
                    value = if (days == 0L) s(R.string.today) else days.toString(),
                    unit = if (days == 0L) "" else s(R.string.unit_days),
                    line1 = greg(next.second, "EEEE d MMMM"),
                    line2 = hijri(next.second),
                    tab = "salaries",
                )
            }
            WidgetType.TodayTasks -> {
                val tasks = TaskGrouping.forToday(c.taskRepository.tasks.first(), today)
                WidgetData.Tasks(
                    rows = tasks.map { WidgetData.TaskRow(it.id, it.title, it.done) },
                    summary = s(R.string.tasks_done, tasks.count { it.done }.toString(), tasks.size.toString()),
                )
            }
            WidgetType.Habit, WidgetType.Habits -> {
                val days = c.habitRepository.doneDays.first()
                val habits = c.habitRepository.habits.first()
                    .filter { config.content.type == WidgetType.Habits || it.id == config.content.itemId }
                if (habits.isEmpty()) return WidgetData.Missing
                WidgetData.Habits(
                    rows = habits.map {
                        val sum = StreakMath.summarize(it, days[it.id].orEmpty(), today)
                        WidgetData.HabitRow(it.id, it.title, it.icon.emoji(), sum.currentStreak, sum.doneToday)
                    },
                    single = config.content.type == WidgetType.Habit,
                )
            }
            WidgetType.Debts, WidgetType.DebtPerson -> {
                val balances = DebtMath.balances(c.debtRepository.people.first(), c.debtRepository.entries.first())
                val currency = s(c.settings.general.value.country.currency)
                if (config.content.type == WidgetType.DebtPerson) {
                    val b = balances.firstOrNull { it.person.id == config.content.itemId } ?: return WidgetData.Missing
                    WidgetData.Big(
                        title = b.person.name,
                        value = Money.format(b.balanceMinor),
                        unit = currency,
                        line1 = s(
                            when {
                                b.balanceMinor > 0 -> R.string.balance_owes_you
                                b.balanceMinor < 0 -> R.string.balance_you_owe
                                else -> R.string.balance_settled
                            },
                        ),
                        line2 = b.lastActivity?.let { greg(it, "d MMM yyyy") }.orEmpty(),
                        tab = "debts",
                    )
                } else {
                    val totals = DebtMath.totals(balances)
                    WidgetData.Debts(
                        owed = Money.format(totals.owedToMeMinor),
                        owe = Money.format(totals.iOweMinor),
                        owedLabel = s(R.string.debts_owed_to_me),
                        oweLabel = s(R.string.debts_i_owe),
                        currency = currency,
                    )
                }
            }
            WidgetType.Tasbih -> {
                val t = c.settings.tasbih.value
                val d = t.dhikr()
                WidgetData.Tasbih(
                    count = t.count % d.count.coerceAtLeast(1),
                    target = d.count,
                    dhikr = d.ar,
                    today = s(R.string.tasbih_today, t.todayCount(today).toString()),
                )
            }
            WidgetType.Verse -> {
                val v = SpiritualContent.verseFor(today)
                WidgetData.Verse("﴿ ${v.ar} ﴾", if (locale.language == "ar") v.refAr else v.refEn)
            }
            WidgetType.Schedule -> {
                val weekend = c.settings.general.value.country.weekend
                val salaries = c.plannerRepository.salaries.first().map { src ->
                    val date = SalarySchedule.next(src, today, weekend)
                    Triple(src.name, date, true)
                }
                val reminders = c.plannerRepository.reminders.first().filter { it.enabled }.mapNotNull { r ->
                    ReminderMath.next(r, now)?.let { Triple(r.title, it.toLocalDate(), false) }
                }
                val rows = (salaries + reminders)
                    .sortedBy { it.second }
                    .map { (name, date, salary) ->
                        val days = ChronoUnit.DAYS.between(today, date)
                        WidgetData.ScheduleRow(name, greg(date, "EEE d MMM"), days, daysText(days), salary)
                    }
                if (rows.isEmpty()) WidgetData.Missing else WidgetData.Schedule(rows)
            }
            WidgetType.NothingClock -> {
                val t = now.toLocalTime()
                val use24 = android.text.format.DateFormat.is24HourFormat(context)
                val hh = String.format(java.util.Locale.ENGLISH, "%02d", if (use24) t.hour else (t.hour % 12).let { if (it == 0) 12 else it })
                val mm = String.format(java.util.Locale.ENGLISH, "%02d", t.minute)
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = CalendarNames.dayEnShort(today.dayOfWeek),
                        big = "$hh:$mm",
                        bigAccent = setOf(2),
                        bottom = when {
                            config.style.variant == 1 -> hijri(today)
                            numeric -> String.format(java.util.Locale.ENGLISH, "%02d/%02d", today.dayOfMonth, today.monthValue)
                            else -> "${today.dayOfMonth} ${CalendarNames.monthsEn[today.monthValue - 1].take(3)}"
                        },
                    ),
                )
            }
            WidgetType.NothingGlyph -> if (config.style.variant == 1) {
                val p = (today.dayOfYear - 1f) / today.lengthOfYear()
                WidgetData.NothingStyle(NothingSpec(top = today.year.toString(), big = "${(p * 100).toInt()}%", bottom = "YEAR", ring = p))
            } else {
                val minutes = now.toLocalTime().toSecondOfDay() / 60
                val p = minutes / 1440f
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = "DAY",
                        big = "${(p * 100).toInt()}%",
                        bottom = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes / 60, minutes % 60),
                        ring = p,
                    ),
                )
            }
            WidgetType.NothingPrayer -> {
                val prefs = c.settings.prayer.value
                val times = prefs.timesFor(today)
                val next = PrayerSchedule.next(now, times, prefs.timesFor(today.plusDays(1)))
                val previous = times.filter { it.prayer.isObligatory }.map { today.atTime(it.time) }.filter { !it.isAfter(now) }.maxOrNull()
                    ?: next.at.minusHours(6)
                val left = java.time.Duration.between(now, next.at).toMinutes().coerceAtLeast(0)
                val total = java.time.Duration.between(previous, next.at).toMinutes().coerceAtLeast(1)
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = s(next.prayer.label),
                        big = String.format(java.util.Locale.ENGLISH, "%02d:%02d", left / 60, left % 60),
                        bigAccent = setOf(2),
                        bottom = "AT " + DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH).format(next.at),
                        bar = 1f - left.toFloat() / total,
                    ),
                )
            }
            WidgetType.NothingCounter -> {
                val counter = c.counterRepository.counters.first().firstOrNull { it.id == config.content.itemId }
                    ?: return WidgetData.Missing
                val reading = CounterMath.read(counter, today)
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = counter.title,
                        big = reading.days.toString(),
                        bottom = when {
                            reading.days == 0L -> "TODAY"
                            reading.phase == CounterPhase.Remaining || reading.phase == CounterPhase.StartsIn -> "DAYS LEFT"
                            else -> "DAYS SINCE"
                        },
                    ),
                )
            }
            WidgetType.NothingDate -> if (config.style.variant == 1) {
                val h = HijriCalendar.fromGregorian(today)
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = if (numeric) String.format(java.util.Locale.ENGLISH, "%02d/%d", h.month, h.year) else CalendarNames.hijriEn[h.month - 1],
                        big = h.day.toString(),
                        bottom = if (numeric) "AH" else "${h.year} AH",
                    ),
                )
            } else {
                WidgetData.NothingStyle(
                    NothingSpec(
                        top = if (numeric) {
                            String.format(java.util.Locale.ENGLISH, "%02d/%d", today.monthValue, today.year)
                        } else {
                            CalendarNames.monthsEn[today.monthValue - 1]
                        },
                        big = today.dayOfMonth.toString(),
                        bottom = CalendarNames.dayEn(today.dayOfWeek),
                    ),
                )
            }
            WidgetType.Custom -> CustomWidgets.resolve(context, CustomSpecJson.decode(config.content.extra), now)
            WidgetType.AnalogClock, WidgetType.DigitalClock -> WidgetData.Clock(
                analog = config.content.type == WidgetType.AnalogClock,
                hijri = hijri(today) + " " + s(R.string.hijri_suffix),
                day = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            )
            WidgetType.Dhikr -> {
                val lists = c.settings.adhkar.value
                val all = (lists.morning + lists.evening).distinctBy { it.text }
                if (all.isEmpty()) return WidgetData.Missing
                val d = all[Math.floorMod(today.toEpochDay(), all.size.toLong()).toInt()]
                WidgetData.Verse(d.text, s(R.string.dhikr_repeat, d.count.toString()))
            }
            WidgetType.Date -> {
                val h = HijriCalendar.fromGregorian(today)
                WidgetData.DateInfo(
                    day = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    gregorian = greg(today, "d MMMM yyyy"),
                    hijri = hijri(today) + " " + s(R.string.hijri_suffix),
                    dayNum = today.dayOfMonth,
                    month = today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                    monthNum = today.monthValue,
                    year = today.year,
                    hDay = h.day,
                    hMonth = res.getStringArray(R.array.hijri_months)[h.month - 1],
                    hMonthNum = h.month,
                    hYear = h.year,
                    monthCal = CalendarNames.monthsAr[today.monthValue - 1],
                    hMonthCal = CalendarNames.hijriAr[h.month - 1],
                )
            }
            WidgetType.WideClock -> WidgetData.WideClock
            WidgetType.WorldClock -> {
                val (a, b) = WorldCities.pair(config.content.extra)
                WidgetData.WorldClock(a, b, locale.language == "ar")
            }
            WidgetType.BigClock -> {
                val t = java.time.LocalTime.now()
                val use24 = android.text.format.DateFormat.is24HourFormat(context)
                val hh = String.format(java.util.Locale.ENGLISH, "%02d", if (use24) t.hour else (t.hour % 12).let { if (it == 0) 12 else it })
                val mm = String.format(java.util.Locale.ENGLISH, "%02d", t.minute)
                fun d(x: String) = if (config.style.arabicDigits) CalendarNames.arabicDigits(x) else x
                WidgetData.BigClock(if (config.style.variant == 1) listOf(d(hh), d(mm)) else listOf(d("$hh:$mm")))
            }
            WidgetType.YearDots -> if (config.style.variant == 1) {
                val len = today.lengthOfMonth()
                WidgetData.Dots(
                    title = today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                    percent = today.dayOfMonth * 100 / len,
                    filled = today.dayOfMonth - 1,
                    total = len,
                )
            } else {
                val len = today.lengthOfYear()
                WidgetData.Dots(
                    title = today.year.toString(),
                    percent = (today.dayOfYear - 1) * 100 / len,
                    filled = today.dayOfYear - 1,
                    total = len,
                )
            }
            WidgetType.MonthCalendar -> {
                val startSunday = config.style.variant != 1
                val days = (0 until 7).map { i ->
                    val dow = if (startSunday) java.time.DayOfWeek.SUNDAY.plus(i.toLong()) else java.time.DayOfWeek.SATURDAY.plus(i.toLong())
                    dow.getDisplayName(if (locale.language == "ar") TextStyle.SHORT else TextStyle.NARROW, locale)
                }
                WidgetData.MonthGrid(java.time.YearMonth.from(today), today, startSunday, days)
            }
            WidgetType.CalligraphyDay -> when (config.style.variant) {
                1 -> WidgetData.Calligraphy(CalendarNames.dayEn(today.dayOfWeek), null, null, latin = true)
                2 -> WidgetData.Calligraphy(CalendarNames.dayEnShort(today.dayOfWeek), today.dayOfMonth.toString(), null, latin = true)
                else -> WidgetData.Calligraphy(CalendarNames.dayAr(today.dayOfWeek), null, null, latin = false)
            }
            WidgetType.CalligraphyMonth -> {
                val h = HijriCalendar.fromGregorian(today)
                val gSub = "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)}"
                val hSub = "${h.day} ${res.getStringArray(R.array.hijri_months)[h.month - 1]}"
                when (config.style.variant) {
                    1 -> WidgetData.Calligraphy(CalendarNames.hijriAr[h.month - 1], h.day.toString(), hSub, latin = false)
                    2 -> WidgetData.Calligraphy(CalendarNames.monthsEn[today.monthValue - 1], today.dayOfMonth.toString(), null, latin = true)
                    3 -> WidgetData.Calligraphy(CalendarNames.hijriEn[h.month - 1], h.day.toString(), null, latin = true)
                    else -> WidgetData.Calligraphy(CalendarNames.monthsAr[today.monthValue - 1], today.dayOfMonth.toString(), gSub, latin = false)
                }
            }
        }
    }
}

fun HabitIcon.emoji(): String = when (this) {
    HabitIcon.Quran -> "📖"
    HabitIcon.Prayer -> "🕌"
    HabitIcon.Dhikr -> "📿"
    HabitIcon.Walk -> "🚶"
    HabitIcon.Sport -> "🏋️"
    HabitIcon.Water -> "💧"
    HabitIcon.Sleep -> "🌙"
    HabitIcon.Read -> "📚"
    HabitIcon.Study -> "🎓"
    HabitIcon.NoSmoking -> "🚭"
    HabitIcon.Charity -> "💝"
    HabitIcon.Star -> "⭐"
}
