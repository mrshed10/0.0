package com.athar.app.feature.reminders

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.calendar.HijriCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Repeat { Once, Daily, Weekly, Monthly, Yearly }

data class Reminder(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val repeat: Repeat = Repeat.Once,
    /** Calendar for Monthly/Yearly repetition (e.g. every 1 Ramadan). */
    val calendar: CalendarKind = CalendarKind.Gregorian,
    val tone: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        fun new(now: LocalDateTime) = Reminder(
            title = "",
            date = now.toLocalDate(),
            time = now.toLocalTime().plusHours(1).withMinute(0).withSecond(0).withNano(0),
        )
    }
}

object ReminderMath {
    /** The [n]th occurrence (0 = the original date/time). */
    fun occurrence(r: Reminder, n: Long): LocalDateTime {
        val day = when (r.repeat) {
            Repeat.Once -> r.date
            Repeat.Daily -> r.date.plusDays(n)
            Repeat.Weekly -> r.date.plusWeeks(n)
            Repeat.Monthly -> if (r.calendar == CalendarKind.Hijri) HijriCalendar.plusMonths(r.date, n) else r.date.plusMonths(n)
            Repeat.Yearly -> if (r.calendar == CalendarKind.Hijri) HijriCalendar.plusYears(r.date, n) else r.date.plusYears(n)
        }
        return day.atTime(r.time)
    }

    /** Next occurrence strictly after [now], or null if a one-time reminder has passed. */
    fun next(r: Reminder, now: LocalDateTime): LocalDateTime? {
        val first = occurrence(r, 0)
        if (first.isAfter(now)) return first
        if (r.repeat == Repeat.Once) return null
        // Jump close to now, then step.
        var n = when (r.repeat) {
            Repeat.Daily -> ChronoUnit.DAYS.between(first, now)
            Repeat.Weekly -> ChronoUnit.WEEKS.between(first, now)
            Repeat.Monthly -> ChronoUnit.MONTHS.between(first, now) - 1
            Repeat.Yearly -> ChronoUnit.YEARS.between(first, now) - 1
            Repeat.Once -> 0
        }.coerceAtLeast(0)
        var guard = 0
        while (guard++ < 1000) {
            val occ = runCatching { occurrence(r, n) }.getOrNull() ?: return null
            if (occ.isAfter(now)) return occ
            n++
        }
        return null
    }
}
