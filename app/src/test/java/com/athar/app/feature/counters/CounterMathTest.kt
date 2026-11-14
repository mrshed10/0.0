package com.athar.app.feature.counters

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.designsystem.theme.AccentTone
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CounterMathTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun counter(id: Long, date: LocalDate, direction: CounterDirection) =
        Counter(id, "c$id", date, direction, AccentTone.Gold, CalendarKind.Gregorian)

    @Test
    fun countdownAheadAndPassed() {
        assertEquals(
            CounterReading(10, CounterPhase.Remaining),
            CounterMath.read(counter(1, today.plusDays(10), CounterDirection.CountDown), today),
        )
        assertEquals(
            CounterReading(0, CounterPhase.Remaining),
            CounterMath.read(counter(1, today, CounterDirection.CountDown), today),
        )
        assertEquals(
            CounterReading(3, CounterPhase.Passed),
            CounterMath.read(counter(1, today.minusDays(3), CounterDirection.CountDown), today),
        )
    }

    @Test
    fun countUpSinceAndStartsIn() {
        assertEquals(
            CounterReading(87, CounterPhase.Since),
            CounterMath.read(counter(1, today.minusDays(87), CounterDirection.CountUp), today),
        )
        assertEquals(
            CounterReading(5, CounterPhase.StartsIn),
            CounterMath.read(counter(1, today.plusDays(5), CounterDirection.CountUp), today),
        )
    }

    @Test
    fun sortUpcomingFirstThenRunningThenPassed() {
        val passed = counter(1, today.minusDays(2), CounterDirection.CountDown)
        val far = counter(2, today.plusDays(90), CounterDirection.CountDown)
        val near = counter(3, today.plusDays(3), CounterDirection.CountDown)
        val since = counter(4, today.minusDays(40), CounterDirection.CountUp)
        assertEquals(listOf(3L, 2L, 4L, 1L), CounterMath.sort(listOf(passed, far, near, since), today).map { it.id })
    }
}
