package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerScheduleTest {
    private val times = listOf(
        PrayerTime(Prayer.Fajr, LocalTime.of(4, 57)),
        PrayerTime(Prayer.Sunrise, LocalTime.of(6, 16)),
        PrayerTime(Prayer.Dhuhr, LocalTime.of(11, 43)),
        PrayerTime(Prayer.Asr, LocalTime.of(14, 47)),
        PrayerTime(Prayer.Maghrib, LocalTime.of(17, 10)),
        PrayerTime(Prayer.Isha, LocalTime.of(18, 40)),
    )

    @Test
    fun sunriseIsSkipped() {
        val next = PrayerSchedule.next(LocalDateTime.of(2026, 11, 14, 5, 30), times)
        assertEquals(Prayer.Dhuhr, next.prayer)
        assertEquals(LocalDateTime.of(2026, 11, 14, 11, 43), next.at)
    }

    @Test
    fun afterIshaIsTomorrowsFajr() {
        val next = PrayerSchedule.next(LocalDateTime.of(2026, 11, 14, 21, 0), times)
        assertEquals(Prayer.Fajr, next.prayer)
        assertEquals(LocalDateTime.of(2026, 11, 15, 4, 57), next.at)
    }

    @Test
    fun afterIshaUsesTomorrowsTimesWhenGiven() {
        val tomorrow = listOf(PrayerTime(Prayer.Fajr, LocalTime.of(4, 58)))
        val next = PrayerSchedule.next(LocalDateTime.of(2026, 11, 14, 21, 0), times, tomorrow)
        assertEquals(LocalDateTime.of(2026, 11, 15, 4, 58), next.at)
    }
}
