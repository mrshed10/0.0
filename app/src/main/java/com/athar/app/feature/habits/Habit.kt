package com.athar.app.feature.habits

import com.athar.app.core.designsystem.theme.AccentTone
import java.time.LocalDate

enum class HabitIcon { Quran, Prayer, Dhikr, Walk, Sport, Water, Sleep, Read, Study, NoSmoking, Charity, Star }

data class Habit(
    val id: Long = 0,
    val title: String,
    val icon: HabitIcon,
    val tone: AccentTone,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        fun new() = Habit(title = "", icon = HabitIcon.Star, tone = AccentTone.Rose)
    }
}

/** A habit plus everything the UI shows about its history. */
data class HabitSummary(
    val habit: Habit,
    val doneDays: Set<LocalDate>,
    val doneToday: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
)

object StreakMath {
    /**
     * Consecutive days ending today, or ending yesterday if today is not
     * done yet (the streak is still alive until the day ends).
     */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun best(days: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    fun summarize(habit: Habit, days: Set<LocalDate>, today: LocalDate) = HabitSummary(
        habit = habit,
        doneDays = days,
        doneToday = today in days,
        currentStreak = current(days, today),
        bestStreak = best(days),
    )
}
