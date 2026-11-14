package com.athar.app.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.athar.app.R
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.calendar.HijriDate
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Numbers use Western digits (0-9) in both languages for now, the common
 * choice in Saudi apps. A "numerals" preference can switch this later in
 * one place.
 */
private val NumberLocale: Locale = Locale.ENGLISH

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun Long.formatNumber(): String = NumberFormat.getIntegerInstance(NumberLocale).format(this)

fun Int.formatNumber(): String = toLong().formatNumber()

fun DayOfWeek.displayName(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)

fun LocalDate.formatGregorian(locale: Locale, pattern: String = "d MMMM yyyy"): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(this)

fun LocalTime.formatClock(locale: Locale, withPeriod: Boolean = true): String =
    DateTimeFormatter.ofPattern(if (withPeriod) "h:mm a" else "h:mm", locale).format(this)

/** e.g. "4 جمادى الأولى 1448 هـ". Month names come from resources, not the JDK. */
@Composable
fun HijriDate.format(withSuffix: Boolean = true): String {
    val months = stringArrayResource(R.array.hijri_months)
    val text = "${day.formatNumber()} ${months[month - 1]} ${year.formatNumber().replace(",", "")}"
    return if (withSuffix) "$text ${stringResource(R.string.hijri_suffix)}" else text
}

@Composable
fun LocalDate.formatHijri(withSuffix: Boolean = true): String =
    HijriCalendar.fromGregorian(this).format(withSuffix)

/** Compact remaining time: "2d 3h", "1h 23m", "12m" (localized units). */
@Composable
fun formatRemaining(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val d = minutes / (24 * 60)
    val h = (minutes % (24 * 60)) / 60
    val m = minutes % 60
    val dU = stringResource(R.string.unit_day_short)
    val hU = stringResource(R.string.unit_hour_short)
    val mU = stringResource(R.string.unit_minute_short)
    return when {
        d > 0 -> "${d.formatNumber()}$dU ${h.formatNumber()}$hU"
        h > 0 -> "${h.formatNumber()}$hU ${m.formatNumber()}$mU"
        else -> "${m.formatNumber()}$mU"
    }
}
