package com.athar.app.feature.salary

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.calendar.HijriDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** What to do when a pay day falls on the weekend. */
enum class WeekendShift { Before, After, None }

/** A recurring monthly payment (salary, pension, support programme...). */
data class SalarySource(
    val id: Long = 0,
    val name: String,
    val payDay: Int,
    val calendar: CalendarKind = CalendarKind.Gregorian,
    val shift: WeekendShift = WeekendShift.Before,
    /** -1 = no reminder, 0 = on the day, n = n days before (at [REMINDER_TIME]). */
    val remindDaysBefore: Int = -1,
    val tone: String? = null,
    val createdAt: Long = 0,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/**
 * Pay-date rules. Saudi defaults: pay day 27 (Gregorian), weekend Fri/Sat,
 * weekend pay days move back to the previous working day.
 */
object SalarySchedule {
    const val SAUDI_PAY_DAY = 27
    val SAUDI_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    fun shifted(date: LocalDate, weekend: Set<DayOfWeek>, shift: WeekendShift): LocalDate {
        var d = date
        val step = when (shift) {
            WeekendShift.Before -> -1L
            WeekendShift.After -> 1L
            WeekendShift.None -> return d
        }
        var guard = 0
        while (d.dayOfWeek in weekend && guard++ < 7) d = d.plusDays(step)
        return d
    }

    fun payDateFor(
        month: YearMonth,
        payDay: Int = SAUDI_PAY_DAY,
        weekend: Set<DayOfWeek> = SAUDI_WEEKEND,
        shift: WeekendShift = WeekendShift.Before,
    ): LocalDate = shifted(month.atDay(payDay.coerceIn(1, month.lengthOfMonth())), weekend, shift)

    /** The next pay date on or after [today] (Gregorian pay day). */
    fun nextPayDate(
        today: LocalDate,
        payDay: Int = SAUDI_PAY_DAY,
        weekend: Set<DayOfWeek> = SAUDI_WEEKEND,
    ): LocalDate {
        val month = YearMonth.from(today)
        val thisMonth = payDateFor(month, payDay, weekend)
        return if (!today.isAfter(thisMonth)) thisMonth else payDateFor(month.plusMonths(1), payDay, weekend)
    }

    /** The next [count] pay dates of [source] on or after [from], in its own calendar. */
    fun upcoming(source: SalarySource, from: LocalDate, weekend: Set<DayOfWeek>, count: Int = 1): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var offset = -1L // start one period back: a shift may pull next month's date earlier
        while (result.size < count && offset < 400) {
            val raw = when (source.calendar) {
                CalendarKind.Gregorian -> {
                    val month = YearMonth.from(from).plusMonths(offset)
                    month.atDay(source.payDay.coerceIn(1, month.lengthOfMonth()))
                }
                CalendarKind.Hijri -> {
                    val anchor = HijriCalendar.plusMonths(from, offset)
                    val h = HijriCalendar.fromGregorian(anchor)
                    val length = HijriCalendar.lengthOfMonth(h.year, h.month)
                    HijriCalendar.toGregorian(HijriDate(h.year, h.month, source.payDay.coerceIn(1, length)))
                }
            }
            val date = shifted(raw, weekend, source.shift)
            if (!date.isBefore(from) && date !in result) result += date
            offset++
        }
        return result
    }

    fun next(source: SalarySource, from: LocalDate, weekend: Set<DayOfWeek>): LocalDate =
        upcoming(source, from, weekend, 1).first()
}
