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
    data class NextPrayer(val name: String, val time: String, val place: String, val rows: List<PrayerRow>) : WidgetData
    data class Big(val title: String, val value: String, val unit: String, val line1: String, val line2: String, val tab: String) : WidgetData
    data class TaskRow(val id: Long, val title: String, val done: Boolean)
    data class Tasks(val rows: List<TaskRow>, val summary: String) : WidgetData
    data class HabitRow(val id: Long, val title: String, val emoji: String, val streak: Int, val doneToday: Boolean)
    data class Habits(val rows: List<HabitRow>, val single: Boolean) : WidgetData
    data class Debts(val owed: String, val owe: String, val owedLabel: String, val oweLabel: String, val currency: String) : WidgetData
    data class Tasbih(val count: Int, val target: Int, val dhikr: String, val today: String) : WidgetData
    data class Verse(val text: String, val ref: String) : WidgetData
    data class DateInfo(val day: String, val gregorian: String, val hijri: String) : WidgetData
}

object WidgetDataLoader {
    suspend fun load(context: Context, config: WidgetConfig?): WidgetData {
        if (config == null) return WidgetData.Unconfigured
        val c = (context.applicationContext as AtharApplication).container
        val locale = context.resources.configuration.locales[0]
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val res = context.resources
        fun s(id: Int, vararg args: Any) = res.getString(id, *args)
        fun hijri(date: LocalDate): String {
            val h = HijriCalendar.fromGregorian(date)
            return "${h.day} ${res.getStringArray(R.array.hijri_months)[h.month - 1]} ${h.year}"
        }
        fun greg(date: LocalDate, pattern: String = "EEE d MMM") = DateTimeFormatter.ofPattern(pattern, locale).format(date)
        fun clock(t: java.time.LocalTime) = DateTimeFormatter.ofPattern("h:mm a", locale).format(t)

        return when (config.content.type) {
            WidgetType.NextPrayer -> {
                val prefs = c.settings.prayer.value
                val times = prefs.timesFor(today)
                val next = PrayerSchedule.next(now, times, prefs.timesFor(today.plusDays(1)))
                WidgetData.NextPrayer(
                    name = s(next.prayer.label),
                    time = clock(next.at.toLocalTime()),
                    place = Cities.byId(prefs.cityId)?.name(locale) ?: s(R.string.my_location),
                    rows = times.map {
                        WidgetData.PrayerRow(s(it.prayer.label), clock(it.time), it.prayer == next.prayer)
                    },
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
            WidgetType.Date -> WidgetData.DateInfo(
                day = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                gregorian = greg(today, "d MMMM yyyy"),
                hijri = hijri(today) + " " + s(R.string.hijri_suffix),
            )
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
