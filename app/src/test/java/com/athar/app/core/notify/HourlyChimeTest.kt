package com.athar.app.core.notify

import com.athar.app.core.data.ChimeSettings
import com.athar.app.core.data.ChimeSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HourlyChimeTest {
    private val on = ChimeSettings(enabled = true, quietHours = true, quietFrom = 22, quietTo = 7)

    @Test
    fun quietHoursCrossMidnight() {
        assertTrue(on.isQuiet(23))
        assertTrue(on.isQuiet(3))
        assertFalse(on.isQuiet(7))
        assertFalse(on.isQuiet(21))
        assertFalse(on.copy(quietHours = false).isQuiet(3))
        val day = ChimeSettings(enabled = true, quietFrom = 13, quietTo = 15)
        assertTrue(day.isQuiet(14))
        assertFalse(day.isQuiet(15))
    }

    @Test
    fun nextChimeIsTopOfNextAllowedHour() {
        val now = LocalDateTime.of(2026, 11, 16, 14, 25, 10)
        assertEquals(LocalDateTime.of(2026, 11, 16, 15, 0), HourlyChime.nextAt(now, on))
        // 21:59 -> 22:00 is quiet, so the next one is 07:00 tomorrow.
        assertEquals(LocalDateTime.of(2026, 11, 17, 7, 0), HourlyChime.nextAt(LocalDateTime.of(2026, 11, 16, 21, 59), on))
        assertNull(HourlyChime.nextAt(now, on.copy(enabled = false)))
    }

    @Test
    fun soundsAreShortAndAudible() {
        ChimeSound.entries.forEach { sound ->
            val samples = HourlyChime.samples(sound)
            val seconds = samples.size.toFloat() / HourlyChime.SAMPLE_RATE
            assertTrue("$sound length $seconds", seconds in 0.05f..0.6f)
            assertTrue(samples.maxOf { it.toInt() } > 5_000)
            // Soft fade-in: no click at the start.
            assertTrue(kotlin.math.abs(samples.first().toInt()) < 500)
        }
    }
}
