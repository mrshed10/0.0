package com.athar.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TasbihTest {
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun tapCountsTodayAndLifetime() {
        val s = TasbihState().tapped(today).tapped(today)
        assertEquals(2, s.count)
        assertEquals(2, s.todayCount(today))
        assertEquals(2L, s.lifetime)
    }

    @Test
    fun todayTotalResetsOnNewDay() {
        val yesterday = TasbihState().tapped(today.minusDays(1)).tapped(today.minusDays(1))
        val s = yesterday.tapped(today)
        assertEquals(1, s.todayCount(today))
        assertEquals(0, yesterday.todayCount(today))
        assertEquals(3L, s.lifetime)
    }
}
