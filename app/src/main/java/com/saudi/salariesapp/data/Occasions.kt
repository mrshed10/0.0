package com.saudi.salariesapp.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay

/** المناسبات الوطنية (ميلادية ثابتة) والدينية (هجرية وفق أم القرى). */
object Occasions {

    private data class Gregorian(val key: String, val title: String, val day: MonthDay, val color: String)
    private data class Hijri(val key: String, val title: String, val month: Int, val day: Int, val color: String)

    private val gregorian = listOf(
        Gregorian("founding_day", "يوم التأسيس", MonthDay.of(2, 22), "#795548"),
        Gregorian("flag_day", "يوم العلم", MonthDay.of(3, 11), "#2E7D32"),
        Gregorian("national_day", "اليوم الوطني", MonthDay.of(9, 23), "#007A5E"),
    )

    private val hijri = listOf(
        Hijri("ramadan", "بداية شهر رمضان", 9, 1, "#5E35B1"),
        Hijri("eid_fitr", "عيد الفطر", 10, 1, "#C2185B"),
        Hijri("arafah", "يوم عرفة", 12, 9, "#EF6C00"),
        Hijri("eid_adha", "عيد الأضحى", 12, 10, "#D84315"),
        Hijri("hijri_new_year", "رأس السنة الهجرية", 1, 1, "#00838F"),
    )

    fun upcoming(today: LocalDate): List<EventModel> {
        val events = mutableListOf<EventModel>()
        for (g in gregorian) {
            var date = g.day.atYear(today.year)
            if (date.isBefore(today)) date = g.day.atYear(today.year + 1)
            events += event(g.key, g.title, date, g.color, today)
        }
        val hToday = HijriCalendar.toHijri(today)
        for (h in hijri) {
            val date = listOf(hToday.year, hToday.year + 1)
                .mapNotNull { HijriCalendar.fromHijri(it, h.month, h.day) }
                .firstOrNull { !it.isBefore(today) }
                ?: continue
            events += event(h.key, h.title, date, h.color, today)
        }
        return events.sortedBy { it.target }
    }

    private fun event(key: String, title: String, date: LocalDate, color: String, today: LocalDate) =
        EventModel(
            id = "occasion_${key}_$date",
            title = title,
            dateHijri = HijriCalendar.formatHijri(date),
            dateGregorian = HijriCalendar.formatGregorian(date),
            remainingDays = PaymentSchedule.daysBetween(today, date),
            category = EventCategory.OCCASIONS,
            colorHex = color,
            target = LocalDateTime.of(date, LocalTime.MIDNIGHT),
        )
}
