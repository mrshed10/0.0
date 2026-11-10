package com.saudi.salariesapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth

/** What to do when a payment day falls on the Saudi weekend (Friday / Saturday). */
enum class WeekendShift {
    /** Pay on the nominal day even if it is a weekend. */
    NONE,
    /** Friday -> Thursday, Saturday -> Sunday (Ministry of Finance rule for state salaries). */
    NEAREST_WORKDAY,
    /** Friday or Saturday -> the Thursday before. */
    PREVIOUS_WORKDAY,
}

/**
 * A monthly payment that falls on a fixed day of each Gregorian month.
 *
 * NOTE: the days below are the commonly published schedule. Government bodies can and do
 * announce exceptions (e.g. early payment before Eid); edit this list in one place if a rule changes.
 */
data class MonthlyPaymentRule(
    val id: String,
    val title: String,
    val category: Category,
    val dayOfMonth: Int,
    val shift: WeekendShift,
)

/** A yearly occasion fixed in either the Gregorian or the Hijri calendar. */
sealed class OccasionRule(open val id: String, open val title: String) {
    data class Gregorian(override val id: String, override val title: String, val monthDay: MonthDay) :
        OccasionRule(id, title)

    data class Hijri(override val id: String, override val title: String, val month: Int, val day: Int) :
        OccasionRule(id, title)
}

object PaymentSchedule {

    val monthlyRules = listOf(
        MonthlyPaymentRule("salary", "رواتب الموظفين", Category.SALARIES, 27, WeekendShift.NEAREST_WORKDAY),
        MonthlyPaymentRule("retirement", "رواتب التقاعد", Category.RETIREMENT, 1, WeekendShift.PREVIOUS_WORKDAY),
        MonthlyPaymentRule("citizen", "حساب المواطن", Category.CITIZEN, 10, WeekendShift.PREVIOUS_WORKDAY),
        MonthlyPaymentRule("social", "الضمان الاجتماعي المطوّر", Category.SOCIAL_SECURITY, 1, WeekendShift.PREVIOUS_WORKDAY),
        MonthlyPaymentRule("sakani", "الدعم السكني", Category.SAKANI, 24, WeekendShift.PREVIOUS_WORKDAY),
        MonthlyPaymentRule("rehab", "التأهيل الشامل", Category.REHAB, 1, WeekendShift.PREVIOUS_WORKDAY),
    )

    val occasionRules = listOf(
        OccasionRule.Gregorian("founding_day", "يوم التأسيس", MonthDay.of(2, 22)),
        OccasionRule.Gregorian("national_day", "اليوم الوطني", MonthDay.of(9, 23)),
        OccasionRule.Hijri("ramadan", "بداية شهر رمضان", 9, 1),
        OccasionRule.Hijri("eid_fitr", "عيد الفطر", 10, 1),
        OccasionRule.Hijri("arafah", "يوم عرفة", 12, 9),
        OccasionRule.Hijri("eid_adha", "عيد الأضحى", 12, 10),
        OccasionRule.Hijri("hijri_new_year", "رأس السنة الهجرية", 1, 1),
    )

    /** Payments are shown counting down to the start of the payment day. */
    private val PAYMENT_TIME: LocalTime = LocalTime.MIDNIGHT

    fun applyShift(date: LocalDate, shift: WeekendShift): LocalDate = when (shift) {
        WeekendShift.NONE -> date
        WeekendShift.NEAREST_WORKDAY -> when (date.dayOfWeek) {
            DayOfWeek.FRIDAY -> date.minusDays(1)
            DayOfWeek.SATURDAY -> date.plusDays(1)
            else -> date
        }
        WeekendShift.PREVIOUS_WORKDAY -> when (date.dayOfWeek) {
            DayOfWeek.FRIDAY -> date.minusDays(1)
            DayOfWeek.SATURDAY -> date.minusDays(2)
            else -> date
        }
    }

    /** The actual payment date of [rule] for the Gregorian month [month]. */
    fun paymentDate(rule: MonthlyPaymentRule, month: YearMonth): LocalDate {
        val day = rule.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        return applyShift(month.atDay(day), rule.shift)
    }

    /** The next [count] payment dates on or after [today]. */
    fun upcomingPayments(rule: MonthlyPaymentRule, today: LocalDate, count: Int): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        // Start one month back: a payment shifted earlier may land in the previous month
        // (e.g. the 1st falls on a Saturday -> paid on the previous Thursday).
        var month = YearMonth.from(today).minusMonths(1)
        while (result.size < count) {
            val d = paymentDate(rule, month)
            if (!d.isBefore(today)) result += d
            month = month.plusMonths(1)
        }
        return result
    }

    fun nextPayment(rule: MonthlyPaymentRule, today: LocalDate): LocalDate =
        upcomingPayments(rule, today, 1).first()

    fun nextOccurrence(rule: OccasionRule, today: LocalDate): LocalDate = when (rule) {
        is OccasionRule.Gregorian -> {
            val thisYear = rule.monthDay.atYear(today.year)
            if (thisYear.isBefore(today)) rule.monthDay.atYear(today.year + 1) else thisYear
        }
        is OccasionRule.Hijri -> {
            val hYear = HijriDates.toHijri(today).year
            val thisYear = HijriDates.toGregorian(hYear, rule.month, rule.day)
            if (thisYear.isBefore(today)) HijriDates.toGregorian(hYear + 1, rule.month, rule.day) else thisYear
        }
    }

    /** Title such as "رواتب الموظفين - نوفمبر" for a payment on [date]. */
    fun paymentTitle(rule: MonthlyPaymentRule, date: LocalDate): String {
        // A payment shifted into the previous month still belongs to the nominal month.
        val nominal = if (rule.dayOfMonth <= 2 && date.dayOfMonth > 20) date.plusMonths(1) else date
        return "${rule.title} - ${HijriDates.gregorianMonths[nominal.monthValue - 1]}"
    }

    fun paymentEvent(rule: MonthlyPaymentRule, date: LocalDate) = EventModel(
        id = rule.id,
        title = paymentTitle(rule, date),
        target = date.atTime(PAYMENT_TIME),
        category = rule.category,
    )

    fun occasionEvent(rule: OccasionRule, today: LocalDate) = EventModel(
        id = rule.id,
        title = rule.title,
        target = nextOccurrence(rule, today).atStartOfDay(),
        category = Category.OCCASIONS,
        calendarType = if (rule is OccasionRule.Hijri) CalendarType.HIJRI else CalendarType.GREGORIAN,
    )

    /** Every built-in event (next occurrence of each), nearest first. */
    fun builtInEvents(today: LocalDate): List<EventModel> =
        (monthlyRules.map { paymentEvent(it, nextPayment(it, today)) } +
            occasionRules.map { occasionEvent(it, today) })
            .sortedBy { it.target }

    fun rulesFor(vararg categories: Category): List<MonthlyPaymentRule> =
        monthlyRules.filter { it.category in categories }
}

/** Built-in events plus the user's countdowns, nearest first. */
fun allEvents(today: LocalDate, custom: List<EventModel>): List<EventModel> =
    (PaymentSchedule.builtInEvents(today) + custom).sortedBy { it.target }
