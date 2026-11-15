package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class CalendarNamesTest {
    @Test
    fun arabicDigits() {
        assertEquals("١٢:٣٤", CalendarNames.arabicDigits("12:34"))
        assertEquals("٠٩", CalendarNames.arabicDigits("09"))
    }

    @Test
    fun namesCoverAllDaysAndMonths() {
        assertEquals(7, CalendarNames.daysAr.size)
        assertEquals(12, CalendarNames.monthsAr.size)
        assertEquals(12, CalendarNames.hijriAr.size)
        assertEquals("SUNDAY", CalendarNames.dayEn(DayOfWeek.SUNDAY))
        assertEquals("الأَحَد", CalendarNames.dayAr(DayOfWeek.SUNDAY))
    }

    @Test
    fun worldCityPairFallsBack() {
        val (a, b) = WorldCities.pair("")
        assertEquals("brussels", a.id)
        assertEquals("dubai", b.id)
        val (c, d) = WorldCities.pair("tokyo,unknown")
        assertEquals("tokyo", c.id)
        assertEquals("dubai", d.id)
    }
}
