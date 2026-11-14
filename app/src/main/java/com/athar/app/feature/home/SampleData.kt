package com.athar.app.feature.home

import com.athar.app.R
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.calendar.HijriDate
import com.athar.app.core.designsystem.theme.AccentTone
import com.athar.app.core.ui.UiText
import com.athar.app.feature.prayer.Prayer
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.salary.SalarySchedule
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Placeholder content so the home screen can be built and reviewed before the
 * Room database (counters, tasks, habits, debts) and the prayer-time
 * calculator exist. Prayer times are fixed sample values, not real ones.
 */
object SampleData {
    fun homeState(now: LocalDateTime): HomeUiState {
        val today = now.toLocalDate()
        val ramadanYear = HijriCalendar.fromGregorian(today).let { h -> if (h.month < 9) h.year else h.year + 1 }
        return HomeUiState(
            now = now,
            prayers = listOf(
                PrayerTime(Prayer.Fajr, LocalTime.of(4, 57)),
                PrayerTime(Prayer.Sunrise, LocalTime.of(6, 16)),
                PrayerTime(Prayer.Dhuhr, LocalTime.of(11, 43)),
                PrayerTime(Prayer.Asr, LocalTime.of(14, 47)),
                PrayerTime(Prayer.Maghrib, LocalTime.of(17, 10)),
                PrayerTime(Prayer.Isha, LocalTime.of(18, 40)),
            ),
            prayersAreSample = true,
            payDay = SalarySchedule.SAUDI_PAY_DAY,
            counters = listOf(
                CounterItem(
                    id = 1,
                    title = UiText.Res(R.string.sample_counter_ramadan),
                    date = HijriCalendar.toGregorian(HijriDate(ramadanYear, 9, 1)),
                    direction = CounterDirection.CountDown,
                    tone = AccentTone.Gold,
                ),
                CounterItem(
                    id = 2,
                    title = UiText.Res(R.string.sample_counter_trip),
                    date = today.plusDays(23),
                    direction = CounterDirection.CountDown,
                    tone = AccentTone.Teal,
                ),
                CounterItem(
                    id = 3,
                    title = UiText.Res(R.string.sample_counter_quit),
                    date = today.minusDays(87),
                    direction = CounterDirection.CountUp,
                    tone = AccentTone.Rose,
                ),
            ),
            tasks = listOf(
                TaskItem(1, UiText.Res(R.string.sample_task_1), done = false),
                TaskItem(2, UiText.Res(R.string.sample_task_2), done = true),
                TaskItem(3, UiText.Res(R.string.sample_task_3), done = false),
                TaskItem(4, UiText.Res(R.string.sample_task_4), done = false),
            ),
            habits = listOf(
                HabitItem(1, UiText.Res(R.string.sample_habit_quran), HabitIcon.Quran, streak = 12, doneToday = true),
                HabitItem(2, UiText.Res(R.string.sample_habit_walk), HabitIcon.Walk, streak = 5, doneToday = false),
                HabitItem(3, UiText.Res(R.string.sample_habit_water), HabitIcon.Water, streak = 21, doneToday = true),
                HabitItem(4, UiText.Res(R.string.sample_habit_sleep), HabitIcon.Sleep, streak = 0, doneToday = false),
            ),
            debts = DebtSummary(owedToMe = 4_250, iOwe = 1_100, people = 6),
            tasbihCount = 0,
        )
    }
}
