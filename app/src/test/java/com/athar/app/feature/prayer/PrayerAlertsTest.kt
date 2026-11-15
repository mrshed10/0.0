package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerAlertsTest {
    @Test
    fun preOffsetsSpreadEvenly() {
        assertEquals(listOf(15), PrayerAlert(preEnabled = true, preMinutes = 15, preCount = 1).preOffsets())
        assertEquals(listOf(15, 10, 5), PrayerAlert(preEnabled = true, preMinutes = 15, preCount = 3).preOffsets())
        assertEquals(listOf(10, 5), PrayerAlert(preEnabled = true, preMinutes = 10, preCount = 2).preOffsets())
        assertEquals(emptyList<Int>(), PrayerAlert(preEnabled = false).preOffsets())
    }

    @Test
    fun defaultIqamaTimes() {
        assertEquals(25, PrayerAlert.default(Prayer.Fajr).iqamaMinutes)
        assertEquals(10, PrayerAlert.default(Prayer.Maghrib).iqamaMinutes)
        assertEquals(20, PrayerAlert.default(Prayer.Isha).iqamaMinutes)
    }

    @Test
    fun silentWindowStartsAtIqama() {
        val adhan = LocalDateTime.of(2026, 11, 16, 12, 0) // Monday
        val w = PrayerAlertMath.silentWindow(Prayer.Dhuhr, adhan, PrayerAlert(iqamaMinutes = 20), SilentPrefs(enabled = true, durationMinutes = 25))!!
        assertEquals(LocalDateTime.of(2026, 11, 16, 12, 20), w.start)
        assertEquals(LocalDateTime.of(2026, 11, 16, 12, 45), w.end)
        assertEquals(false, w.jumuah)
    }

    @Test
    fun jumuahReplacesDhuhrOnFriday() {
        val friday = LocalDateTime.of(2026, 11, 20, 11, 50)
        val w = PrayerAlertMath.silentWindow(
            Prayer.Dhuhr,
            friday,
            PrayerAlert(iqamaMinutes = 20),
            SilentPrefs(enabled = true, prayers = emptySet(), jumuah = true, jumuahStartAfter = 0, jumuahDuration = 60),
        )!!
        assertTrue(w.jumuah)
        assertEquals(friday, w.start)
        assertEquals(friday.plusMinutes(60), w.end)
    }

    @Test
    fun noWindowWhenDisabledOrNotSelected() {
        val adhan = LocalDateTime.of(2026, 11, 16, 15, 0)
        assertNull(PrayerAlertMath.silentWindow(Prayer.Asr, adhan, PrayerAlert(), SilentPrefs(enabled = false)))
        assertNull(PrayerAlertMath.silentWindow(Prayer.Asr, adhan, PrayerAlert(), SilentPrefs(enabled = true, prayers = setOf(Prayer.Fajr))))
    }

    private fun day(f: String, s: String, d: String, a: String, m: String, i: String) = listOf(
        PrayerTime(Prayer.Fajr, LocalTime.parse(f)),
        PrayerTime(Prayer.Sunrise, LocalTime.parse(s)),
        PrayerTime(Prayer.Dhuhr, LocalTime.parse(d)),
        PrayerTime(Prayer.Asr, LocalTime.parse(a)),
        PrayerTime(Prayer.Maghrib, LocalTime.parse(m)),
        PrayerTime(Prayer.Isha, LocalTime.parse(i)),
    )
    private val times = day("05:00", "06:20", "11:45", "14:50", "17:10", "18:40")

    @Test
    fun currentPrayerWindowAndIqama() {
        val now = LocalDateTime.of(2026, 11, 16, 15, 0)
        val c = PrayerWindow.current(now, times, times, times) { 20 }!!
        assertEquals(Prayer.Asr, c.prayer)
        assertEquals(Duration.ofMinutes(10), c.elapsed(now))
        assertEquals(Duration.ofMinutes(130), c.left(now)) // until Maghrib 17:10
        assertEquals(LocalDateTime.of(2026, 11, 16, 15, 10), c.iqamaAt)
    }

    @Test
    fun noPrayerBetweenSunriseAndDhuhr() {
        assertNull(PrayerWindow.current(LocalDateTime.of(2026, 11, 16, 9, 0), times, times, times) { null })
    }

    @Test
    fun ishaLastsUntilIslamicMidnight() {
        // Maghrib 17:10 → next Fajr 05:00: midnight = 23:05
        val late = PrayerWindow.current(LocalDateTime.of(2026, 11, 16, 23, 0), times, times, times) { null }!!
        assertEquals(Prayer.Isha, late.prayer)
        assertEquals(LocalDateTime.of(2026, 11, 16, 23, 5), late.end)
        assertNull(PrayerWindow.current(LocalDateTime.of(2026, 11, 16, 23, 30), times, times, times) { null })
        // After midnight: yesterday's Isha window is over until Fajr
        assertNull(PrayerWindow.current(LocalDateTime.of(2026, 11, 17, 2, 0), times, times, times) { null })
    }

    @Test
    fun jsonRoundTrip() {
        val alerts = PrayerPrefsDefaults.alerts().mapValues { (p, a) ->
            if (p == Prayer.Fajr) a.copy(preEnabled = true, preMinutes = 30, preCount = 3, preTone = "content://t", iqamaAlert = true) else a
        }
        assertEquals(alerts, PrayerAlertsJson.decodeAlerts(PrayerAlertsJson.encodeAlerts(alerts)))
        val silent = SilentPrefs(enabled = true, prayers = setOf(Prayer.Fajr, Prayer.Isha), mode = SilentMode.Vibrate, durationMinutes = 30, jumuah = false)
        assertEquals(silent, PrayerAlertsJson.decodeSilent(PrayerAlertsJson.encodeSilent(silent)))
    }
}

private object PrayerPrefsDefaults {
    fun alerts() = Prayer.entries.filter { it.isObligatory }.associateWith { PrayerAlert.default(it) }
}
