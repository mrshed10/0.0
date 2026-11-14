package com.athar.app.feature.home

import com.athar.app.R
import com.athar.app.core.ui.UiText
import com.athar.app.feature.prayer.Prayer
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.salary.SalarySchedule
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Placeholder content for sections that are not built yet (prayer times,
 * habits, debts). Counters and tasks come from the database.
 * Prayer times are fixed sample values, not real ones.
 */
object SampleData {
    fun homeState(now: LocalDateTime) = HomeUiState(
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
        counters = emptyList(),
        todayTasks = emptyList(),
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
