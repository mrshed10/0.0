package com.athar.app.feature.home

import com.athar.app.core.ui.UiText
import com.athar.app.feature.counters.Counter
import com.athar.app.feature.prayer.NextPrayer
import com.athar.app.feature.prayer.PrayerSchedule
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.tasks.Task
import java.time.LocalDate
import java.time.LocalDateTime

enum class HabitIcon { Quran, Walk, Water, Sleep }

data class HabitItem(
    val id: Long,
    val title: UiText,
    val icon: HabitIcon,
    val streak: Int,
    val doneToday: Boolean,
)

data class DebtSummary(val owedToMe: Long, val iOwe: Long, val people: Int)

data class HomeUiState(
    val now: LocalDateTime,
    val prayers: List<PrayerTime>,
    val prayersAreSample: Boolean,
    val payDay: Int,
    /** From the database. */
    val counters: List<Counter>,
    /** From the database: overdue + due today + completed today. */
    val todayTasks: List<Task>,
    val habits: List<HabitItem>,
    val debts: DebtSummary,
    val tasbihCount: Int,
) {
    val today: LocalDate get() = now.toLocalDate()
    val nextPrayer: NextPrayer get() = PrayerSchedule.next(now, prayers)
    val nextPayDate: LocalDate get() = SalarySchedule.nextPayDate(today, payDay)
}
