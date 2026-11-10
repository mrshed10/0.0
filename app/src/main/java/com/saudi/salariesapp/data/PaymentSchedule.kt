package com.saudi.salariesapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * خدمة صرف دورية (راتب، معاش، دعم...).
 * [defaultDay] يوم الصرف الميلادي المعتاد. يستطيع المستخدم تعديله من التطبيق
 * إذا تغيّر الموعد الرسمي، فالقيم هنا افتراضية وليست مصدراً رسمياً.
 */
data class PaymentService(
    val id: String,
    val name: String,
    val category: EventCategory,
    val defaultDay: Int,
    val colorHex: String,
)

object PaymentSchedule {

    val services = listOf(
        PaymentService("gov_salary", "رواتب الموظفين", EventCategory.SALARIES, 27, "#007A5E"),
        PaymentService("retirement", "معاشات التقاعد", EventCategory.RETIREMENT, 1, "#1E88E5"),
        PaymentService("gosi", "معاشات التأمينات الاجتماعية", EventCategory.RETIREMENT, 1, "#3949AB"),
        PaymentService("citizen_account", "حساب المواطن", EventCategory.CITIZEN, 10, "#00A896"),
        PaymentService("social_security", "الضمان الاجتماعي المطوّر", EventCategory.SUPPORT, 1, "#8E24AA"),
        PaymentService("sakani", "الدعم السكني (سكني)", EventCategory.SUPPORT, 24, "#F4511E"),
        PaymentService("rehab", "إعانة التأهيل الشامل", EventCategory.SUPPORT, 5, "#6D4C41"),
    )

    fun service(id: String): PaymentService? = services.firstOrNull { it.id == id }

    /** عطلة نهاية الأسبوع في المملكة: الجمعة والسبت. */
    fun isWeekend(date: LocalDate) =
        date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY

    /** إذا وافق يوم الصرف عطلة نهاية الأسبوع يُقدَّم إلى يوم العمل السابق (الخميس). */
    fun adjustForWeekend(date: LocalDate): LocalDate {
        var d = date
        while (isWeekend(d)) d = d.minusDays(1)
        return d
    }

    /** تاريخ الصرف لشهر معيّن، مع ضبط اليوم لآخر الشهر إذا كان الشهر أقصر. */
    fun paymentDateFor(month: YearMonth, day: Int): LocalDate {
        val safeDay = day.coerceIn(1, month.lengthOfMonth())
        return adjustForWeekend(month.atDay(safeDay))
    }

    /** دفعة واحدة: الشهر الذي تُنسب إليه وتاريخ صرفها الفعلي بعد ضبط العطلة. */
    data class Payment(val month: YearMonth, val date: LocalDate)

    /** المواعيد القادمة (اليوم نفسه يُعتبر قادماً) لعدد من الأشهر. */
    fun upcomingPayments(today: LocalDate, day: Int, count: Int): List<Payment> {
        val result = mutableListOf<Payment>()
        // نبدأ من الشهر السابق لأن صرف أول الشهر قد يُقدَّم إلى أواخر الشهر الحالي
        var month = YearMonth.from(today).minusMonths(1)
        while (result.size < count) {
            val candidate = paymentDateFor(month, day)
            if (!candidate.isBefore(today)) result += Payment(month, candidate)
            month = month.plusMonths(1)
        }
        return result
    }

    fun nextPayment(today: LocalDate, day: Int): Payment = upcomingPayments(today, day, 1).first()

    fun toEvent(service: PaymentService, payment: Payment, today: LocalDate): EventModel {
        val date = payment.date
        return EventModel(
            id = "${service.id}_${payment.month}",
            title = "${service.name} - ${HijriCalendar.gregorianMonths[payment.month.monthValue - 1]}",
            dateHijri = HijriCalendar.formatHijri(date),
            dateGregorian = HijriCalendar.formatGregorian(date),
            remainingDays = daysBetween(today, date),
            category = service.category,
            colorHex = service.colorHex,
            target = LocalDateTime.of(date, LocalTime.MIDNIGHT),
        )
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt()
}
