package com.athar.app.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HijriCalendarTest {
    @Test
    fun gregorianToUmmAlQura() {
        assertEquals(HijriDate(1448, 6, 4), HijriCalendar.fromGregorian(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun ramadanStartDates() {
        assertEquals(LocalDate.of(2026, 2, 18), HijriCalendar.toGregorian(HijriDate(1447, 9, 1)))
        assertEquals(LocalDate.of(2027, 2, 8), HijriCalendar.toGregorian(HijriDate(1448, 9, 1)))
    }

    @Test
    fun roundTrip() {
        val date = LocalDate.of(2027, 6, 1)
        assertEquals(date, HijriCalendar.toGregorian(HijriCalendar.fromGregorian(date)))
    }

    @Test
    fun plusMonthsClampsToShorterMonth() {
        // 30 Sha'ban 1448 (2027-02-07) + 1 month -> 29 Ramadan 1448 (Ramadan 1448 has 29 days)
        val start = HijriCalendar.toGregorian(HijriDate(1448, 8, 30))
        assertEquals(LocalDate.of(2027, 2, 7), start)
        assertEquals(LocalDate.of(2027, 3, 8), HijriCalendar.plusMonths(start, 1))
        assertEquals(HijriDate(1448, 9, 29), HijriCalendar.fromGregorian(HijriCalendar.plusMonths(start, 1)))
    }

    @Test
    fun plusYears() {
        assertEquals(LocalDate.of(2027, 11, 3), HijriCalendar.plusYears(LocalDate.of(2026, 11, 14), 1))
    }
}
