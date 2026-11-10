package com.saudi.salariesapp.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Category(val label: String, val defaultColorHex: String) {
    SALARIES("الرواتب", "#007A5E"),
    RETIREMENT("التقاعد", "#1E88E5"),
    CITIZEN("حساب المواطن", "#00A896"),
    SOCIAL_SECURITY("الضمان الاجتماعي", "#8E24AA"),
    SAKANI("سكني", "#F4511E"),
    REHAB("التأهيل الشامل", "#6D4C41"),
    OCCASIONS("المناسبات", "#C0A062"),
    CUSTOM("عداد شخصي", "#3949AB"),
}

enum class CalendarType { GREGORIAN, HIJRI }

/**
 * One countdown target: a salary/support payment, an occasion, or a user-created countdown.
 *
 * Unlike a stored `remainingDays`, the remaining time is computed from [target] so it is
 * always correct, whenever it is displayed (in the app or in the widget).
 */
data class EventModel(
    val id: String,
    val title: String,                // مثل: راتب نوفمبر، موعد شخصي، إجازة
    val target: LocalDateTime,        // the moment the countdown reaches zero
    val category: Category,           // رواتب، تقاعد، مواطن، مناسبات، شخصي
    val colorHex: String = category.defaultColorHex,  // لون البطاقة أو اليدجت
    val calendarType: CalendarType = CalendarType.GREGORIAN,
    val isCustom: Boolean = false,
) {
    val date: LocalDate get() = target.toLocalDate()
    val dateHijri: String get() = HijriDates.formatHijri(date)
    val dateGregorian: String get() = HijriDates.formatGregorian(date)

    /** Whole calendar days from [today] to the event date (0 = today). */
    fun remainingDays(today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, date)

    fun remaining(now: LocalDateTime = LocalDateTime.now()): Remaining = Remaining.between(now, target)
}

data class Remaining(
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val isPast: Boolean,
) {
    companion object {
        fun between(now: LocalDateTime, target: LocalDateTime): Remaining {
            val totalSeconds = ChronoUnit.SECONDS.between(now, target)
            if (totalSeconds <= 0) return Remaining(0, 0, 0, 0, isPast = true)
            return Remaining(
                days = totalSeconds / 86_400,
                hours = ((totalSeconds % 86_400) / 3_600).toInt(),
                minutes = ((totalSeconds % 3_600) / 60).toInt(),
                seconds = (totalSeconds % 60).toInt(),
                isPast = false,
            )
        }
    }
}

/** Arabic plural for a number of days: يوم / يومان / أيام / يوماً */
fun daysLabel(n: Long): String = when {
    n == 1L -> "يوم"
    n == 2L -> "يومان"
    n in 3..10 -> "أيام"
    else -> "يوماً"
}
