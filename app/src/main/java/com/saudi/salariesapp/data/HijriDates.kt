package com.saudi.salariesapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** A date in the Umm al-Qura (official Saudi) Hijri calendar. Month is 1..12. */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Hijri <-> Gregorian conversion and Arabic formatting.
 *
 * java.time's [HijrahChronology] ("Hijrah-umalqura") ships the official Umm al-Qura
 * tables used by the Saudi government, so no third-party library is needed.
 */
object HijriDates {

    val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    val gregorianMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
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

    fun toHijri(date: LocalDate): HijriDate {
        val h = HijrahDate.from(date)
        return HijriDate(
            year = h.get(ChronoField.YEAR),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            day = h.get(ChronoField.DAY_OF_MONTH)
        )
    }

    fun toGregorian(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.from(HijrahChronology.INSTANCE.date(year, month, day))

    fun toGregorian(h: HijriDate): LocalDate = toGregorian(h.year, h.month, h.day)

    /** 29 or 30, per Umm al-Qura. */
    fun lengthOfMonth(year: Int, month: Int): Int =
        HijrahChronology.INSTANCE.date(year, month, 1).lengthOfMonth()

    /** e.g. "16 جمادى الآخرة 1448 هـ" */
    fun formatHijri(date: LocalDate): String {
        val h = toHijri(date)
        return "${h.day} ${hijriMonths[h.month - 1]} ${h.year} هـ"
    }

    /** e.g. "26 نوفمبر 2026 م" */
    fun formatGregorian(date: LocalDate): String =
        "${date.dayOfMonth} ${gregorianMonths[date.monthValue - 1]} ${date.year} م"

    /** e.g. "الثلاثاء 19 جمادى الأولى 1448 هـ" */
    fun formatHijriWithDay(date: LocalDate): String =
        "${dayName(date.dayOfWeek)} ${formatHijri(date)}"
}
