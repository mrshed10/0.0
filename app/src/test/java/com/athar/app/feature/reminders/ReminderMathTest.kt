package com.athar.app.feature.reminders

import com.athar.app.core.calendar.CalendarKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderMathTest {
    private val now = LocalDateTime.of(2026, 11, 14, 10, 0)
    private fun r(date: LocalDate, time: LocalTime, repeat: Repeat, calendar: CalendarKind = CalendarKind.Gregorian) =
        Reminder(title = "t", date = date, time = time, repeat = repeat, calendar = calendar)

    @Test
    fun onceInFutureAndPast() {
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0),
            ReminderMath.next(r(LocalDate.of(2026, 11, 20), LocalTime.of(9, 0), Repeat.Once), now),
        )
        assertNull(ReminderMath.next(r(LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), Repeat.Once), now))
    }

    @Test
    fun dailyLaterTodayOrTomorrow() {
        val start = LocalDate.of(2025, 1, 1)
        assertEquals(LocalDateTime.of(2026, 11, 14, 21, 0), ReminderMath.next(r(start, LocalTime.of(21, 0), Repeat.Daily), now))
        assertEquals(LocalDateTime.of(2026, 11, 15, 8, 0), ReminderMath.next(r(start, LocalTime.of(8, 0), Repeat.Daily), now))
    }

    @Test
    fun weeklyKeepsWeekday() {
        // 2 Nov 2026 is a Monday -> next Monday after 14 Nov is 16 Nov
        val next = ReminderMath.next(r(LocalDate.of(2026, 11, 2), LocalTime.of(7, 30), Repeat.Weekly), now)
        assertEquals(LocalDateTime.of(2026, 11, 16, 7, 30), next)
    }

    @Test
    fun monthlyClampsWithoutDrift() {
        // Started 31 Jan: Feb -> 28, but March is back to 31
        val r = r(LocalDate.of(2026, 1, 31), LocalTime.of(9, 0), Repeat.Monthly)
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), ReminderMath.next(r, now))
        assertEquals(LocalDateTime.of(2027, 3, 31, 9, 0), ReminderMath.next(r, LocalDateTime.of(2027, 3, 1, 0, 0)))
    }

    @Test
    fun yearlyHijri() {
        // Every 1 Ramadan, starting 1 Ramadan 1447 (18 Feb 2026) -> 1 Ramadan 1448 (8 Feb 2027)
        val r = r(LocalDate.of(2026, 2, 18), LocalTime.of(20, 0), Repeat.Yearly, CalendarKind.Hijri)
        assertEquals(LocalDateTime.of(2027, 2, 8, 20, 0), ReminderMath.next(r, now))
    }
}
