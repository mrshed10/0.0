package com.athar.app.feature.counters

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.designsystem.theme.AccentTone
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CounterDirection { CountDown, CountUp }

data class Counter(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val direction: CounterDirection,
    val tone: AccentTone,
    /** The calendar the user entered the date in; shown first. */
    val calendar: CalendarKind,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        fun new(today: LocalDate) = Counter(
            title = "",
            date = today.plusDays(30),
            direction = CounterDirection.CountDown,
            tone = AccentTone.Gold,
            calendar = CalendarKind.Gregorian,
        )
    }
}

enum class CounterPhase {
    /** Countdown, event still ahead (0 = today). */
    Remaining,
    /** Countdown whose date has passed. */
    Passed,
    /** Count-up running since the date (0 = started today). */
    Since,
    /** Count-up whose start date is still ahead. */
    StartsIn,
}

data class CounterReading(val days: Long, val phase: CounterPhase)

object CounterMath {
    fun read(counter: Counter, today: LocalDate): CounterReading {
        val diff = ChronoUnit.DAYS.between(today, counter.date)
        return when (counter.direction) {
            CounterDirection.CountDown ->
                if (diff >= 0) CounterReading(diff, CounterPhase.Remaining) else CounterReading(-diff, CounterPhase.Passed)
            CounterDirection.CountUp ->
                if (diff <= 0) CounterReading(-diff, CounterPhase.Since) else CounterReading(diff, CounterPhase.StartsIn)
        }
    }

    /** Upcoming first (nearest first), then running count-ups, then passed countdowns. */
    fun sort(counters: List<Counter>, today: LocalDate): List<Counter> =
        counters.sortedWith(
            compareBy<Counter> {
                when (read(it, today).phase) {
                    CounterPhase.Remaining -> 0
                    CounterPhase.StartsIn -> 1
                    CounterPhase.Since -> 2
                    CounterPhase.Passed -> 3
                }
            }.thenBy { read(it, today).days },
        )
}
