package com.saudi.salariesapp.data

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * تحويل التاريخ بين الهجري والميلادي وفق تقويم أم القرى الرسمي.
 * يعتمد على HijrahChronology المضمّنة في Java/Android (بيانات "Hijrah-umalqura")،
 * فلا حاجة لمكتبة خارجية.
 */
object HijriCalendar {

    val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    val gregorianMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "السبت"
        DayOfWeek.SUNDAY -> "الأحد"
        DayOfWeek.MONDAY -> "الاثنين"
        DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"
        DayOfWeek.THURSDAY -> "الخميس"
        DayOfWeek.FRIDAY -> "الجمعة"
    }

    data class HijriParts(val year: Int, val month: Int, val day: Int)

    fun toHijri(date: LocalDate): HijriParts {
        val h = HijrahDate.from(date)
        return HijriParts(
            year = h.get(ChronoField.YEAR),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            day = h.get(ChronoField.DAY_OF_MONTH),
        )
    }

    /** يرجع null إذا كان التاريخ الهجري غير صالح (مثلاً 30 في شهر من 29 يوماً). */
    fun fromHijri(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.from(HijrahChronology.INSTANCE.date(year, month, day))
    } catch (e: DateTimeException) {
        null
    }

    fun lengthOfHijriMonth(year: Int, month: Int): Int? = try {
        HijrahChronology.INSTANCE.date(year, month, 1).lengthOfMonth()
    } catch (e: DateTimeException) {
        null
    }

    fun formatHijri(date: LocalDate): String {
        val h = toHijri(date)
        return "${h.day} ${hijriMonths[h.month - 1]} ${h.year} هـ"
    }

    fun formatGregorian(date: LocalDate): String =
        "${date.dayOfMonth} ${gregorianMonths[date.monthValue - 1]} ${date.year} م"

    fun formatFull(date: LocalDate): String = "${dayName(date.dayOfWeek)} ${formatHijri(date)}"
}
