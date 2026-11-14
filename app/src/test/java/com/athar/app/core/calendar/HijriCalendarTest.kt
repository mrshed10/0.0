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
}
