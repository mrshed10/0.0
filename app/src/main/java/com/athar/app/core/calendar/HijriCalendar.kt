package com.athar.app.core.calendar

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** A Hijri (Umm al-Qura) date. Month is 1..12. */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Hijri <-> Gregorian conversion using the JDK's built-in Umm al-Qura
 * calendar (the official Saudi calendar), so it works fully offline.
 */
object HijriCalendar {
    fun fromGregorian(date: LocalDate): HijriDate {
        val h = HijrahDate.from(date)
        return HijriDate(
            year = h.get(ChronoField.YEAR),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            day = h.get(ChronoField.DAY_OF_MONTH),
        )
    }

    fun toGregorian(date: HijriDate): LocalDate =
        LocalDate.from(HijrahChronology.INSTANCE.date(date.year, date.month, date.day))
}
