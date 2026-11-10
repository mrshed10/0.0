package com.saudi.salariesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalculatorsTest {

    @Test
    fun ageInGregorianAndHijri() {
        val age = Calculators.age(LocalDate.of(2000, 1, 15), LocalDate.of(2026, 11, 10))!!
        assertEquals(26, age.years)
        assertEquals(9, age.months)
        assertEquals(26, age.days)
        assertEquals(27, age.hijriYears) // السنة الهجرية أقصر بنحو 11 يوماً
        assertNull(Calculators.age(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun vatAddedAndExtracted() {
        val add = Calculators.price(100.0, includesVat = false)
        assertEquals(15.0, add.vat, 1e-9)
        assertEquals(115.0, add.total, 1e-9)

        val extract = Calculators.price(115.0, includesVat = true)
        assertEquals(100.0, extract.beforeVat, 1e-9)

        val discounted = Calculators.price(200.0, includesVat = false, discountPercent = 10.0)
        assertEquals(180.0, discounted.beforeVat, 1e-9)
        assertEquals(207.0, discounted.total, 1e-9)
    }

    @Test
    fun netSalaryEstimate() {
        val s = Calculators.salary(basic = 10_000.0, allowances = 2_500.0)
        assertEquals(12_500.0, s.gross, 1e-9)
        assertEquals(900.0, s.deduction, 1e-9)
        assertEquals(11_600.0, s.net, 1e-9)
    }
}
