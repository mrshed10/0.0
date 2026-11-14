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
}
