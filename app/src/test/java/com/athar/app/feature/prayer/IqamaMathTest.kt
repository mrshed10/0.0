package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class IqamaMathTest {
    private val adhan = NextPrayer(Prayer.Dhuhr, LocalDateTime.of(2026, 11, 17, 11, 45))

    @Test
    fun iqamaFollowsTheAdhan() {
        assertEquals(LocalDateTime.of(2026, 11, 17, 12, 5), IqamaMath.iqamaAt(adhan, PrayerAlert(iqamaMinutes = 20)))
        assertNull(IqamaMath.iqamaAt(adhan, PrayerAlert(iqamaEnabled = false)))
        assertNull(IqamaMath.iqamaAt(adhan, PrayerAlert(iqamaMinutes = 0)))
    }

    @Test
    fun preAlertDefaultsToTenMinutes() {
        assertEquals(listOf(10), PrayerAlert(preEnabled = true).preOffsets())
    }
}
