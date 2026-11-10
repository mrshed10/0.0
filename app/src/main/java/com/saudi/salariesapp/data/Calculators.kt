package com.saudi.salariesapp.data

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** منطق الحاسبات (منفصل عن الواجهة لسهولة الاختبار). */
object Calculators {

    data class Age(val years: Int, val months: Int, val days: Int, val totalDays: Long, val hijriYears: Int)

    fun age(birth: LocalDate, today: LocalDate): Age? {
        if (birth.isAfter(today)) return null
        val p = Period.between(birth, today)
        val hb = HijriCalendar.toHijri(birth)
        val ht = HijriCalendar.toHijri(today)
        var hijriYears = ht.year - hb.year
        if (ht.month < hb.month || (ht.month == hb.month && ht.day < hb.day)) hijriYears--
        return Age(p.years, p.months, p.days, ChronoUnit.DAYS.between(birth, today), hijriYears)
    }

    const val VAT_RATE = 0.15

    data class Price(val beforeVat: Double, val vat: Double, val total: Double)

    /** [includesVat]: هل السعر المُدخل شامل للضريبة؟ [discountPercent] خصم يطبّق قبل الضريبة. */
    fun price(amount: Double, includesVat: Boolean, discountPercent: Double = 0.0): Price {
        val base = if (includesVat) amount / (1 + VAT_RATE) else amount
        val discounted = base * (1 - discountPercent.coerceIn(0.0, 100.0) / 100)
        val vat = discounted * VAT_RATE
        return Price(discounted, vat, discounted + vat)
    }

    data class Salary(val gross: Double, val deduction: Double, val net: Double)

    /**
     * تقدير صافي الراتب: الأساسي + البدلات − نسبة الاستقطاع من الأساسي
     * (الافتراضي 9% لنظام التقاعد المدني، ويمكن تعديله).
     */
    fun salary(basic: Double, allowances: Double, deductionPercent: Double = 9.0): Salary {
        val deduction = basic * deductionPercent / 100
        val gross = basic + allowances
        return Salary(gross, deduction, gross - deduction)
    }
}
