package com.saudi.salariesapp.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** الوقت المتبقي مفصّلاً للعداد التنازلي المباشر. */
data class Remaining(val days: Long, val hours: Long, val minutes: Long, val seconds: Long) {
    val isOver get() = days == 0L && hours == 0L && minutes == 0L && seconds == 0L
}

object Countdown {
    fun remaining(now: LocalDateTime, target: LocalDateTime): Remaining {
        val d = Duration.between(now, target)
        if (d.isNegative || d.isZero) return Remaining(0, 0, 0, 0)
        val total = d.seconds
        return Remaining(
            days = total / 86_400,
            hours = (total % 86_400) / 3_600,
            minutes = (total % 3_600) / 60,
            seconds = total % 60,
        )
    }

    fun toEvent(c: CustomCountdown, today: LocalDate): EventModel {
        val date = c.target.toLocalDate()
        return EventModel(
            id = c.id,
            title = c.title,
            dateHijri = HijriCalendar.formatHijri(date),
            dateGregorian = HijriCalendar.formatGregorian(date),
            remainingDays = PaymentSchedule.daysBetween(today, date),
            category = EventCategory.PERSONAL,
            colorHex = c.colorHex,
            target = c.target,
            isCustom = true,
        )
    }

    /** نص مختصر يناسب البطاقات والودجت. */
    fun label(days: Int): String = when {
        days < 0 -> "انتهى"
        days == 0 -> "اليوم"
        days == 1 -> "غداً"
        days == 2 -> "يومان"
        days in 3..10 -> "$days أيام"
        else -> "$days يوماً"
    }
}
