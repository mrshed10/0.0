package com.athar.app.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesWesternAndArabicDigits() {
        assertEquals(150_000L, Money.parse("1500"))
        assertEquals(150_050L, Money.parse("1,500.5"))
        assertEquals(150_075L, Money.parse("١٥٠٠٫٧٥"))
        assertEquals(1_234L, Money.parse("۱۲٫۳۴"))
        assertEquals(5L, Money.parse("0.05"))
    }

    @Test
    fun rejectsInvalid() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun formats() {
        assertEquals("1,250", Money.format(125_000))
        assertEquals("1,250.50", Money.format(125_050))
        assertEquals("0.05", Money.format(5))
        assertEquals("300", Money.format(-30_000))
    }

    @Test
    fun inputRoundTrip() {
        assertEquals("1250", Money.toInput(125_000))
        assertEquals("1250.5", Money.toInput(125_050))
        assertEquals("0.05", Money.toInput(5))
        listOf(125_000L, 125_050L, 5L, 99L).forEach { assertEquals(it, Money.parse(Money.toInput(it))) }
    }
}
