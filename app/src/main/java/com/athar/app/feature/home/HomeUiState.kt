package com.athar.app.feature.home

import com.athar.app.core.data.TasbihState
import com.athar.app.feature.counters.Counter
import com.athar.app.feature.debts.DebtTotals
import com.athar.app.feature.habits.HabitSummary
import com.athar.app.feature.prayer.NextPrayer
import com.athar.app.feature.prayer.PrayerSchedule
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.feature.prayer.SpiritualContent
import com.athar.app.feature.tasks.Task
import java.time.LocalDate
import java.time.LocalDateTime

data class NextSalary(val name: String, val date: LocalDate)

data class HomeUiState(
    val now: LocalDateTime,
    val prayers: List<PrayerTime> = emptyList(),
    val tomorrowPrayers: List<PrayerTime> = emptyList(),
    val cityId: String? = null,
    val counters: List<Counter> = emptyList(),
    /** Overdue + due today + completed today. */
    val todayTasks: List<Task> = emptyList(),
    val habits: List<HabitSummary> = emptyList(),
    val debts: DebtTotals = DebtTotals.Empty,
    val tasbih: TasbihState = TasbihState(),
    val nextSalary: NextSalary? = null,
) {
    val today: LocalDate get() = now.toLocalDate()
    val nextPrayer: NextPrayer? get() = if (prayers.isEmpty()) null else PrayerSchedule.next(now, prayers, tomorrowPrayers)
    val verse: SpiritualContent.Verse get() = SpiritualContent.verseFor(today)
}
