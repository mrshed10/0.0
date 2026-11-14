package com.athar.app.feature.home

import com.athar.app.feature.counters.Counter
import com.athar.app.feature.debts.DebtTotals
import com.athar.app.feature.habits.HabitSummary
import com.athar.app.feature.prayer.NextPrayer
import com.athar.app.feature.prayer.PrayerSchedule
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.tasks.Task
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUiState(
    val now: LocalDateTime,
    val prayers: List<PrayerTime>,
    val prayersAreSample: Boolean,
    val payDay: Int,
    /** From the database. */
    val counters: List<Counter>,
    /** From the database: overdue + due today + completed today. */
    val todayTasks: List<Task>,
    /** From the database. */
    val habits: List<HabitSummary>,
    /** From the database. */
    val debts: DebtTotals,
    val tasbihCount: Int,
) {
    val today: LocalDate get() = now.toLocalDate()
    val nextPrayer: NextPrayer get() = PrayerSchedule.next(now, prayers)
    val nextPayDate: LocalDate get() = SalarySchedule.nextPayDate(today, payDay)
}
