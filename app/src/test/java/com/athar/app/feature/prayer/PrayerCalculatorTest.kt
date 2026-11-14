package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class PrayerCalculatorTest {
    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    private fun makkah(date: LocalDate) =
        PrayerCalculator.times(date, 21.3891, 39.8579, PrayerMethod.UmmAlQura, AsrMadhab.Standard, riyadhZone)
            .associate { it.prayer to it.time }

    private fun assertNear(expected: LocalTime, actual: LocalTime?, minutes: Long = 8) {
        val diff = abs(Duration.between(expected, actual!!).toMinutes())
        assertTrue("expected ~$expected but was $actual", diff <= minutes)
    }

    @Test
    fun makkahMidNovemberIsPlausible() {
        val t = makkah(LocalDate.of(2026, 11, 14))
        assertNear(LocalTime.of(5, 12), t[Prayer.Fajr])
        assertNear(LocalTime.of(6, 30), t[Prayer.Sunrise])
        assertNear(LocalTime.of(12, 13), t[Prayer.Dhuhr])
        assertNear(LocalTime.of(15, 20), t[Prayer.Asr])
        assertNear(LocalTime.of(17, 40), t[Prayer.Maghrib])
        // Prayers are in order
        val ordered = Prayer.entries.map { t[it]!! }
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun ummAlQuraIshaIs90MinutesAfterMaghrib() {
        val t = makkah(LocalDate.of(2026, 11, 14))
        assertEquals(90L, Duration.between(t[Prayer.Maghrib], t[Prayer.Isha]).toMinutes())
    }

    @Test
    fun ummAlQuraIshaIs120MinutesInRamadan() {
        // 20 Feb 2027 is in Ramadan 1448
        val t = makkah(LocalDate.of(2027, 2, 20))
        assertEquals(120L, Duration.between(t[Prayer.Maghrib], t[Prayer.Isha]).toMinutes())
    }

    @Test
    fun hanafiAsrIsLater() {
        val date = LocalDate.of(2026, 11, 14)
        val standard = PrayerCalculator.times(date, 24.7136, 46.6753, PrayerMethod.UmmAlQura, AsrMadhab.Standard, riyadhZone)
        val hanafi = PrayerCalculator.times(date, 24.7136, 46.6753, PrayerMethod.UmmAlQura, AsrMadhab.Hanafi, riyadhZone)
        assertTrue(hanafi.first { it.prayer == Prayer.Asr }.time.isAfter(standard.first { it.prayer == Prayer.Asr }.time))
    }

    @Test
    fun qiblaFromRiyadhPointsWest() {
        val q = PrayerCalculator.qibla(24.7136, 46.6753)
        assertTrue("qibla was $q", q in 235.0..250.0)
    }
}
