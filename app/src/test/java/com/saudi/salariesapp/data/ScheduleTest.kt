package com.saudi.salariesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ScheduleTest {

    @Test
    fun hijriConversionMatchesUmmAlQura() {
        // 1 رمضان 1447 = 18 فبراير 2026 (تقويم أم القرى)
        assertEquals(LocalDate.of(2026, 2, 18), HijriCalendar.fromHijri(1447, 9, 1))
        val h = HijriCalendar.toHijri(LocalDate.of(2026, 2, 18))
        assertEquals(HijriCalendar.HijriParts(1447, 9, 1), h)
    }

    @Test
    fun invalidHijriDateReturnsNull() {
        assertNull(HijriCalendar.fromHijri(1447, 13, 1))
        assertNull(HijriCalendar.fromHijri(1447, 1, 31))
    }

    @Test
    fun weekendPaymentMovesToThursday() {
        // 27 يونيو 2026 يوم سبت → الخميس 25 يونيو
        val date = PaymentSchedule.paymentDateFor(YearMonth.of(2026, 6), 27)
        assertEquals(LocalDate.of(2026, 6, 25), date)
        assertEquals(DayOfWeek.THURSDAY, date.dayOfWeek)
    }

    @Test
    fun paymentDayClampedToShortMonth() {
        // يوم 31 في فبراير 2026 → 28 فبراير (سبت) → الخميس 26
        assertEquals(LocalDate.of(2026, 2, 26), PaymentSchedule.paymentDateFor(YearMonth.of(2026, 2), 31))
    }

    @Test
    fun nextSalaryFromToday() {
        val today = LocalDate.of(2026, 11, 10)
        val next = PaymentSchedule.nextPayment(today, 27)
        assertEquals(LocalDate.of(2026, 11, 26), next.date) // 27 نوفمبر جمعة → الخميس 26
        assertEquals(16, PaymentSchedule.daysBetween(today, next.date))
    }

    @Test
    fun firstOfMonthMovedBackKeepsItsMonth() {
        // 1 أغسطس 2026 سبت → الخميس 30 يوليو، لكنه صرف شهر أغسطس
        val p = PaymentSchedule.nextPayment(LocalDate.of(2026, 7, 29), 1)
        assertEquals(LocalDate.of(2026, 7, 30), p.date)
        assertEquals(YearMonth.of(2026, 8), p.month)
    }

    @Test
    fun paymentTodayIsStillUpcoming() {
        val today = LocalDate.of(2026, 12, 10) // خميس
        assertEquals(today, PaymentSchedule.nextPayment(today, 10).date)
    }

    @Test
    fun upcomingPaymentsAreSortedAndOnWorkdays() {
        val list = PaymentSchedule.upcomingPayments(LocalDate.of(2026, 1, 1), 27, 12)
        assertEquals(12, list.size)
        assertEquals(list.sortedBy { it.date }, list)
        list.forEach { assertFalse(PaymentSchedule.isWeekend(it.date)) }
    }

    @Test
    fun occasionsAreUpcomingAndSorted() {
        val today = LocalDate.of(2026, 11, 10)
        val list = Occasions.upcoming(today)
        assertTrue(list.isNotEmpty())
        list.forEach { assertTrue(it.remainingDays >= 0) }
        assertEquals(list.sortedBy { it.target }, list)
        assertEquals(LocalDate.of(2027, 2, 22), list.first { it.title == "يوم التأسيس" }.target.toLocalDate())
    }

    @Test
    fun countdownBreakdown() {
        val now = LocalDateTime.of(2026, 11, 10, 10, 0, 0)
        val r = Countdown.remaining(now, LocalDateTime.of(2026, 11, 12, 13, 30, 15))
        assertEquals(Remaining(2, 3, 30, 15), r)
        assertTrue(Countdown.remaining(now, now.minusDays(1)).isOver)
    }

    @Test
    fun arabicDayLabels() {
        assertEquals("اليوم", Countdown.label(0))
        assertEquals("يومان", Countdown.label(2))
        assertEquals("5 أيام", Countdown.label(5))
        assertEquals("16 يوماً", Countdown.label(16))
    }
}
