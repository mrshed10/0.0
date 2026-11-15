package com.athar.app.widget

import java.time.DayOfWeek

/** Names written with full diacritics for calligraphy widgets, plus English forms. */
object CalendarNames {
    /** Indexed by [DayOfWeek.value] - 1 (Monday first). */
    val daysAr = listOf("الإِثْنَيْن", "الثُّلَاثَاء", "الأَرْبِعَاء", "الخَمِيس", "الجُمُعَة", "السَّبْت", "الأَحَد")
    val daysEn = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    val daysEnShort = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    val monthsAr = listOf(
        "يَنَايِر", "فِبْرَايِر", "مَارِس", "أَبْرِيل", "مَايُو", "يُونْيُو",
        "يُولْيُو", "أُغُسْطُس", "سِبْتَمْبِر", "أُكْتُوبَر", "نُوفَمْبِر", "دِيسَمْبِر",
    )
    val monthsEn = listOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
    )
    val hijriAr = listOf(
        "مُحَرَّم", "صَفَر", "رَبِيعُ الأَوَّل", "رَبِيعُ الآخِر", "جُمَادَى الأُولَى", "جُمَادَى الآخِرَة",
        "رَجَب", "شَعْبَان", "رَمَضَان", "شَوَّال", "ذُو القَعْدَة", "ذُو الحِجَّة",
    )
    val hijriEn = listOf(
        "MUHARRAM", "SAFAR", "RABI I", "RABI II", "JUMADA I", "JUMADA II",
        "RAJAB", "SHA'BAN", "RAMADAN", "SHAWWAL", "DHU AL-QIDAH", "DHU AL-HIJJAH",
    )

    fun dayAr(d: DayOfWeek) = daysAr[d.value - 1]
    fun dayEn(d: DayOfWeek) = daysEn[d.value - 1]
    fun dayEnShort(d: DayOfWeek) = daysEnShort[d.value - 1]

    /** Converts Western digits to Arabic-Indic (١٢٣). */
    fun arabicDigits(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
}

/** Cities for the world clock (time zones are handled by the system clock views). */
data class WorldCity(val id: String, val nameAr: String, val nameEn: String, val zone: String) {
    fun name(arabic: Boolean) = if (arabic) nameAr else nameEn
}

object WorldCities {
    val all = listOf(
        WorldCity("makkah", "مكة", "Makkah", "Asia/Riyadh"),
        WorldCity("riyadh", "الرياض", "Riyadh", "Asia/Riyadh"),
        WorldCity("dubai", "دبي", "Dubai", "Asia/Dubai"),
        WorldCity("kuwait", "الكويت", "Kuwait", "Asia/Kuwait"),
        WorldCity("doha", "الدوحة", "Doha", "Asia/Qatar"),
        WorldCity("cairo", "القاهرة", "Cairo", "Africa/Cairo"),
        WorldCity("amman", "عمّان", "Amman", "Asia/Amman"),
        WorldCity("istanbul", "إسطنبول", "Istanbul", "Europe/Istanbul"),
        WorldCity("rabat", "الرباط", "Rabat", "Africa/Casablanca"),
        WorldCity("london", "لندن", "London", "Europe/London"),
        WorldCity("brussels", "بروكسل", "Brussels", "Europe/Brussels"),
        WorldCity("paris", "باريس", "Paris", "Europe/Paris"),
        WorldCity("berlin", "برلين", "Berlin", "Europe/Berlin"),
        WorldCity("newyork", "نيويورك", "New York", "America/New_York"),
        WorldCity("toronto", "تورنتو", "Toronto", "America/Toronto"),
        WorldCity("losangeles", "لوس أنجلوس", "Los Angeles", "America/Los_Angeles"),
        WorldCity("karachi", "كراتشي", "Karachi", "Asia/Karachi"),
        WorldCity("delhi", "نيودلهي", "New Delhi", "Asia/Kolkata"),
        WorldCity("kualalumpur", "كوالالمبور", "Kuala Lumpur", "Asia/Kuala_Lumpur"),
        WorldCity("jakarta", "جاكرتا", "Jakarta", "Asia/Jakarta"),
        WorldCity("tokyo", "طوكيو", "Tokyo", "Asia/Tokyo"),
        WorldCity("sydney", "سيدني", "Sydney", "Australia/Sydney"),
    )
    const val DEFAULT = "brussels,dubai"

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Two cities from a "a,b" setting, with sensible fallbacks. */
    fun pair(extra: String): Pair<WorldCity, WorldCity> {
        val ids = extra.ifBlank { DEFAULT }.split(',')
        val a = byId(ids.getOrElse(0) { "" }) ?: byId("brussels")!!
        val b = byId(ids.getOrElse(1) { "" }) ?: byId("dubai")!!
        return a to b
    }
}
