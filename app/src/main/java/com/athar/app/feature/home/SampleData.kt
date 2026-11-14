package com.athar.app.feature.home

import com.athar.app.feature.debts.DebtTotals
import com.athar.app.feature.prayer.Prayer
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.salary.SalarySchedule
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Placeholder content for sections that are not built yet (prayer times).
 * Counters, tasks, habits and debts come from the database.
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
        habits = emptyList(),
        debts = DebtTotals.Empty,
        tasbihCount = 0,
    )
}
