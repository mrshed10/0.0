package com.athar.app.feature.salary

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pay-date rules. Saudi defaults: pay day 27 (Gregorian), weekend Fri/Sat;
 * a pay day that lands on the weekend moves back to the previous working day.
 * Both are parameters so other countries can be added.
 */
object SalarySchedule {
    const val SAUDI_PAY_DAY = 27
    val SAUDI_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    fun payDateFor(
        month: YearMonth,
        payDay: Int = SAUDI_PAY_DAY,
        weekend: Set<DayOfWeek> = SAUDI_WEEKEND,
    ): LocalDate {
        var date = month.atDay(payDay.coerceIn(1, month.lengthOfMonth()))
        while (date.dayOfWeek in weekend) date = date.minusDays(1)
        return date
    }

    /** The next pay date on or after [today]. */
    fun nextPayDate(
        today: LocalDate,
        payDay: Int = SAUDI_PAY_DAY,
        weekend: Set<DayOfWeek> = SAUDI_WEEKEND,
    ): LocalDate {
        val month = YearMonth.from(today)
        val thisMonth = payDateFor(month, payDay, weekend)
        return if (!today.isAfter(thisMonth)) thisMonth else payDateFor(month.plusMonths(1), payDay, weekend)
    }
}
