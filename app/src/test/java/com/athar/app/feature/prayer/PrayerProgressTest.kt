package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerProgressTest {
    private val day = LocalDate.of(2026, 11, 16)
    private fun times(isha: String = "19:00") = listOf(
        PrayerTime(Prayer.Fajr, LocalTime.parse("05:00")),
        PrayerTime(Prayer.Sunrise, LocalTime.parse("06:20")),
        PrayerTime(Prayer.Dhuhr, LocalTime.parse("12:00")),
        PrayerTime(Prayer.Asr, LocalTime.parse("15:00")),
        PrayerTime(Prayer.Maghrib, LocalTime.parse("17:30")),
        PrayerTime(Prayer.Isha, LocalTime.parse(isha)),
    )

    @Test
    fun previousSkipsSunriseAndCrossesMidnight() {
        val morning = day.atTime(8, 0)
        assertEquals(Prayer.Fajr, PrayerSchedule.previous(morning, times()).prayer)
        val night = day.atTime(3, 0)
        val prev = PrayerSchedule.previous(night, times(), times("19:10"))
        assertEquals(Prayer.Isha, prev.prayer)
        assertEquals(day.minusDays(1).atTime(19, 10), prev.at)
    }

    @Test
    fun progressBetweenPrayers() {
        val prev = day.atTime(12, 0)
        val next = day.atTime(15, 0)
        assertEquals(0.5f, PrayerSchedule.progress(day.atTime(13, 30), prev, next), 0.001f)
        assertEquals(0f, PrayerSchedule.progress(day.atTime(11, 0), prev, next), 0.001f)
        assertEquals(1f, PrayerSchedule.progress(LocalDateTime.of(day, LocalTime.of(16, 0)), prev, next), 0.001f)
    }
}
