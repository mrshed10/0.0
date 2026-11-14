package com.athar.app.feature.salary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SalaryScheduleTest {
    @Test
    fun weekdayPayDayIsUnchanged() {
        // 27 Oct 2026 is a Tuesday
        assertEquals(LocalDate.of(2026, 10, 27), SalarySchedule.payDateFor(YearMonth.of(2026, 10)))
    }

    @Test
    fun fridayMovesToThursday() {
        // 27 Nov 2026 is a Friday
        assertEquals(LocalDate.of(2026, 11, 26), SalarySchedule.payDateFor(YearMonth.of(2026, 11)))
    }

    @Test
    fun saturdayMovesToThursday() {
        // 27 Jun 2026 is a Saturday
        assertEquals(LocalDate.of(2026, 6, 25), SalarySchedule.payDateFor(YearMonth.of(2026, 6)))
    }

    @Test
    fun nextPayDateThisMonthOrNext() {
        assertEquals(LocalDate.of(2026, 11, 26), SalarySchedule.nextPayDate(LocalDate.of(2026, 11, 14)))
        assertEquals(LocalDate.of(2026, 11, 26), SalarySchedule.nextPayDate(LocalDate.of(2026, 11, 26)))
        assertEquals(LocalDate.of(2026, 12, 27), SalarySchedule.nextPayDate(LocalDate.of(2026, 11, 27)))
    }

    @Test
    fun payDayClampedToMonthLength() {
        // Pay day 31 in Feb 2027 -> 28 Feb 2027 (Sunday)
        assertEquals(LocalDate.of(2027, 2, 28), SalarySchedule.payDateFor(YearMonth.of(2027, 2), payDay = 31))
    }

    private val saudiWeekend = SalarySchedule.SAUDI_WEEKEND

    @Test
    fun upcomingGregorianWithShift() {
        val source = SalarySource(name = "s", payDay = 27)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 27), LocalDate.of(2027, 1, 27)),
            SalarySchedule.upcoming(source, LocalDate.of(2026, 11, 14), saudiWeekend, 3),
        )
    }

    @Test
    fun shiftAfterMovesToSunday() {
        // 27 Nov 2026 is Friday -> Sunday 29 Nov
        val source = SalarySource(name = "s", payDay = 27, shift = WeekendShift.After)
        assertEquals(LocalDate.of(2026, 11, 29), SalarySchedule.next(source, LocalDate.of(2026, 11, 14), saudiWeekend))
    }

    @Test
    fun noShiftKeepsWeekendDate() {
        val source = SalarySource(name = "s", payDay = 27, shift = WeekendShift.None)
        assertEquals(LocalDate.of(2026, 11, 27), SalarySchedule.next(source, LocalDate.of(2026, 11, 14), saudiWeekend))
    }

    @Test
    fun hijriPayDay() {
        // 1 Ramadan 1448 = 8 Feb 2027 (Monday)
        val source = SalarySource(name = "s", payDay = 1, calendar = com.athar.app.core.calendar.CalendarKind.Hijri)
        val next = SalarySchedule.upcoming(source, LocalDate.of(2027, 1, 20), saudiWeekend, 2)
        assertEquals(LocalDate.of(2027, 2, 8), next[0])
    }

    @Test
    fun shiftedDateBeforeFromIsSkipped() {
        // On Thursday 26 Nov (the shifted date) it is still today's payment
        val source = SalarySource(name = "s", payDay = 27)
        assertEquals(LocalDate.of(2026, 11, 26), SalarySchedule.next(source, LocalDate.of(2026, 11, 26), saudiWeekend))
        assertEquals(LocalDate.of(2026, 12, 27), SalarySchedule.next(source, LocalDate.of(2026, 11, 27), saudiWeekend))
    }
}
