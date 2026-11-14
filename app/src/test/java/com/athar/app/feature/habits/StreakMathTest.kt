package com.athar.app.feature.habits

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakMathTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test
    fun currentIncludesToday() {
        assertEquals(3, StreakMath.current(days(0, 1, 2, 4), today))
    }

    @Test
    fun currentStillAliveWhenTodayNotDoneYet() {
        assertEquals(2, StreakMath.current(days(1, 2, 5), today))
    }

    @Test
    fun currentBrokenAfterMissedDay() {
        assertEquals(0, StreakMath.current(days(2, 3), today))
        assertEquals(0, StreakMath.current(emptySet(), today))
    }

    @Test
    fun bestIsLongestRun() {
        assertEquals(4, StreakMath.best(days(0, 1, 5, 6, 7, 8, 10)))
        assertEquals(0, StreakMath.best(emptySet()))
    }
}
