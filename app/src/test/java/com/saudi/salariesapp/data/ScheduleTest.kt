package com.saudi.salariesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ScheduleTest {

    private val salary = PaymentSchedule.monthlyRules.first { it.id == "salary" }
    private val retirement = PaymentSchedule.monthlyRules.first { it.id == "retirement" }

    @Test fun ummAlQuraKnownDates() {
        assertEquals(LocalDate.of(2026, 2, 18), HijriDates.toGregorian(1447, 9, 1))   // 1 Ramadan 1447
        assertEquals(LocalDate.of(2026, 3, 20), HijriDates.toGregorian(1447, 10, 1))  // Eid al-Fitr 1447
        assertEquals(HijriDate(1447, 9, 1), HijriDates.toHijri(LocalDate.of(2026, 2, 18)))
    }

    @Test fun hijriRoundTrip() {
        var d = LocalDate.of(2025, 1, 1)
        while (d.year < 2030) {
            assertEquals(d, HijriDates.toGregorian(HijriDates.toHijri(d)))
            d = d.plusDays(1)
        }
    }

    @Test fun salaryOnFridayMovesToThursday() {
        // 27 Nov 2026 is a Friday.
        assertEquals(DayOfWeek.FRIDAY, LocalDate.of(2026, 11, 27).dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 26), PaymentSchedule.paymentDate(salary, YearMonth.of(2026, 11)))
    }

    @Test fun salaryOnSaturdayMovesToSunday() {
        // 27 Feb 2027 is a Saturday.
        assertEquals(DayOfWeek.SATURDAY, LocalDate.of(2027, 2, 27).dayOfWeek)
        assertEquals(LocalDate.of(2027, 2, 28), PaymentSchedule.paymentDate(salary, YearMonth.of(2027, 2)))
    }

    @Test fun retirementShiftedIntoPreviousMonthIsFound() {
        // 1 May 2027 is a Saturday -> paid Thursday 29 April.
        assertEquals(DayOfWeek.SATURDAY, LocalDate.of(2027, 5, 1).dayOfWeek)
        val next = PaymentSchedule.nextPayment(retirement, LocalDate.of(2027, 4, 20))
        assertEquals(LocalDate.of(2027, 4, 29), next)
        assertEquals("رواتب التقاعد - مايو", PaymentSchedule.paymentTitle(retirement, next))
    }

    @Test fun upcomingPaymentsAreOrderedAndNotPast() {
        val today = LocalDate.of(2026, 11, 10)
        val list = PaymentSchedule.upcomingPayments(salary, today, 6)
        assertEquals(6, list.size)
        assertEquals(LocalDate.of(2026, 11, 26), list.first())
        assertTrue(list.zipWithNext().all { (a, b) -> a.isBefore(b) })
        assertTrue(list.all { !it.isBefore(today) })
    }

    @Test fun paymentTodayIsStillUpcoming() {
        assertEquals(LocalDate.of(2026, 11, 26), PaymentSchedule.nextPayment(salary, LocalDate.of(2026, 11, 26)))
    }

    @Test fun hijriOccasionRollsToNextYear() {
        val ramadan = PaymentSchedule.occasionRules.first { it.id == "ramadan" }
        // After 1 Ramadan 1447 has passed, the next one is 1 Ramadan 1448.
        assertEquals(HijriDates.toGregorian(1448, 9, 1), PaymentSchedule.nextOccurrence(ramadan, LocalDate.of(2026, 3, 1)))
    }

    @Test fun builtInEventsSorted() {
        val events = PaymentSchedule.builtInEvents(LocalDate.of(2026, 11, 10))
        assertTrue(events.zipWithNext().all { (a, b) -> !a.target.isAfter(b.target) })
        assertTrue(events.all { it.remainingDays(LocalDate.of(2026, 11, 10)) >= 0 })
    }

    @Test fun remainingBreakdown() {
        val r = Remaining.between(LocalDateTime.of(2026, 11, 10, 8, 0, 0), LocalDateTime.of(2026, 11, 12, 9, 30, 15))
        assertEquals(Remaining(2, 1, 30, 15, isPast = false), r)
        assertTrue(Remaining.between(LocalDateTime.of(2026, 11, 10, 8, 0), LocalDateTime.of(2026, 11, 10, 7, 0)).isPast)
    }

    @Test fun arabicDayPlurals() {
        assertEquals("يوم", daysLabel(1)); assertEquals("يومان", daysLabel(2))
        assertEquals("أيام", daysLabel(5)); assertEquals("يوماً", daysLabel(16))
    }
}
