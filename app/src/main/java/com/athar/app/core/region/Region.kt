package com.athar.app.core.region

import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import com.athar.app.R
import com.athar.app.feature.prayer.PrayerMethod
import java.time.DayOfWeek
import java.util.Locale

private val FriSat = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

/** Country presets: currency, weekend (for pay-day shifting) and default prayer method. */
enum class Country(
    val flag: String,
    @StringRes val label: Int,
    @StringRes val currency: Int,
    val weekend: Set<DayOfWeek>,
    val prayerMethod: PrayerMethod,
    val capitalCityId: String,
) {
    SA("🇸🇦", R.string.country_sa, R.string.currency_sar, FriSat, PrayerMethod.UmmAlQura, "riyadh"),
    AE("🇦🇪", R.string.country_ae, R.string.currency_aed, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), PrayerMethod.Dubai, "dubai"),
    KW("🇰🇼", R.string.country_kw, R.string.currency_kwd, FriSat, PrayerMethod.Kuwait, "kuwait"),
    QA("🇶🇦", R.string.country_qa, R.string.currency_qar, FriSat, PrayerMethod.Qatar, "doha"),
    BH("🇧🇭", R.string.country_bh, R.string.currency_bhd, FriSat, PrayerMethod.UmmAlQura, "manama"),
    OM("🇴🇲", R.string.country_om, R.string.currency_omr, FriSat, PrayerMethod.MuslimWorldLeague, "muscat"),
    EG("🇪🇬", R.string.country_eg, R.string.currency_egp, FriSat, PrayerMethod.Egyptian, "cairo"),
    JO("🇯🇴", R.string.country_jo, R.string.currency_jod, FriSat, PrayerMethod.MuslimWorldLeague, "amman"),
}

data class City(
    val id: String,
    val country: Country,
    val nameAr: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun name(locale: Locale): String = if (locale.language == "ar") nameAr else nameEn
}

object Cities {
    val all: List<City> = listOf(
        City("riyadh", Country.SA, "الرياض", "Riyadh", 24.7136, 46.6753),
        City("makkah", Country.SA, "مكة المكرمة", "Makkah", 21.3891, 39.8579),
        City("madinah", Country.SA, "المدينة المنورة", "Madinah", 24.5247, 39.5692),
        City("jeddah", Country.SA, "جدة", "Jeddah", 21.4858, 39.1925),
        City("dammam", Country.SA, "الدمام", "Dammam", 26.4207, 50.0888),
        City("khobar", Country.SA, "الخبر", "Al Khobar", 26.2172, 50.1971),
        City("jubail", Country.SA, "الجبيل", "Jubail", 27.0046, 49.6460),
        City("ahsa", Country.SA, "الأحساء", "Al Ahsa", 25.3833, 49.5833),
        City("taif", Country.SA, "الطائف", "Taif", 21.2703, 40.4158),
        City("yanbu", Country.SA, "ينبع", "Yanbu", 24.0895, 38.0618),
        City("tabuk", Country.SA, "تبوك", "Tabuk", 28.3835, 36.5662),
        City("abha", Country.SA, "أبها", "Abha", 18.2465, 42.5117),
        City("khamis", Country.SA, "خميس مشيط", "Khamis Mushait", 18.3000, 42.7333),
        City("buraydah", Country.SA, "بريدة", "Buraydah", 26.3592, 43.9818),
        City("hail", Country.SA, "حائل", "Hail", 27.5114, 41.7208),
        City("jazan", Country.SA, "جازان", "Jazan", 16.8892, 42.5511),
        City("najran", Country.SA, "نجران", "Najran", 17.5656, 44.2289),
        City("baha", Country.SA, "الباحة", "Al Baha", 20.0129, 41.4677),
        City("arar", Country.SA, "عرعر", "Arar", 30.9753, 41.0381),
        City("sakaka", Country.SA, "سكاكا", "Sakaka", 29.9697, 40.2064),
        City("dubai", Country.AE, "دبي", "Dubai", 25.2048, 55.2708),
        City("abudhabi", Country.AE, "أبوظبي", "Abu Dhabi", 24.4539, 54.3773),
        City("sharjah", Country.AE, "الشارقة", "Sharjah", 25.3463, 55.4209),
        City("kuwait", Country.KW, "مدينة الكويت", "Kuwait City", 29.3759, 47.9774),
        City("doha", Country.QA, "الدوحة", "Doha", 25.2854, 51.5310),
        City("manama", Country.BH, "المنامة", "Manama", 26.2285, 50.5860),
        City("muscat", Country.OM, "مسقط", "Muscat", 23.5880, 58.3829),
        City("cairo", Country.EG, "القاهرة", "Cairo", 30.0444, 31.2357),
        City("alexandria", Country.EG, "الإسكندرية", "Alexandria", 31.2001, 29.9187),
        City("amman", Country.JO, "عمّان", "Amman", 31.9454, 35.9284),
    )

    fun byId(id: String?): City? = all.firstOrNull { it.id == id }

    /** Nearest known city within [maxKm], used to name a GPS position offline. */
    fun nearest(latitude: Double, longitude: Double, maxKm: Double = 60.0): City? =
        all.map { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }
            ?.first

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

/** Currency symbol for the selected country (e.g. "ر.س"). */
val LocalCurrency = compositionLocalOf { "" }
