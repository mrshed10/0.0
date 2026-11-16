package com.athar.app.feature.prayer

import androidx.annotation.StringRes
import com.athar.app.R
import com.athar.app.core.calendar.HijriCalendar
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

enum class Prayer(@StringRes val label: Int, val isObligatory: Boolean) {
    Fajr(R.string.prayer_fajr, true),
    Sunrise(R.string.prayer_sunrise, false),
    Dhuhr(R.string.prayer_dhuhr, true),
    Asr(R.string.prayer_asr, true),
    Maghrib(R.string.prayer_maghrib, true),
    Isha(R.string.prayer_isha, true),
}

enum class PrayerMethod(@StringRes val label: Int, internal val adhan: CalculationMethod) {
    UmmAlQura(R.string.method_umm_al_qura, CalculationMethod.UMM_AL_QURA),
    MuslimWorldLeague(R.string.method_mwl, CalculationMethod.MUSLIM_WORLD_LEAGUE),
    Egyptian(R.string.method_egyptian, CalculationMethod.EGYPTIAN),
    Karachi(R.string.method_karachi, CalculationMethod.KARACHI),
    Dubai(R.string.method_dubai, CalculationMethod.DUBAI),
    Kuwait(R.string.method_kuwait, CalculationMethod.KUWAIT),
    Qatar(R.string.method_qatar, CalculationMethod.QATAR),
    NorthAmerica(R.string.method_isna, CalculationMethod.NORTH_AMERICA),
    Singapore(R.string.method_singapore, CalculationMethod.SINGAPORE),
}

/** Asr: Shafi/Maliki/Hanbali (shadow = 1x) or Hanafi (2x). */
enum class AsrMadhab { Standard, Hanafi }

data class PrayerTime(val prayer: Prayer, val time: LocalTime)

data class NextPrayer(val prayer: Prayer, val at: LocalDateTime)

/** Offline calculation via the adhan library (astronomical, no internet). */
object PrayerCalculator {
    fun times(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        method: PrayerMethod,
        madhab: AsrMadhab,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PrayerTime> {
        val params = method.adhan.parameters
        params.madhab = if (madhab == AsrMadhab.Hanafi) Madhab.HANAFI else Madhab.SHAFI
        val p = PrayerTimes(
            Coordinates(latitude, longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params,
        )
        fun local(d: Date): LocalTime = d.toInstant().atZone(zone).toLocalTime().withSecond(0).withNano(0)

        var isha = local(p.isha)
        // Umm al-Qura: Isha is 120 min after Maghrib during Ramadan (90 otherwise).
        if (method == PrayerMethod.UmmAlQura && HijriCalendar.fromGregorian(date).month == 9) {
            isha = isha.plusMinutes(30)
        }
        return listOf(
            PrayerTime(Prayer.Fajr, local(p.fajr)),
            PrayerTime(Prayer.Sunrise, local(p.sunrise)),
            PrayerTime(Prayer.Dhuhr, local(p.dhuhr)),
            PrayerTime(Prayer.Asr, local(p.asr)),
            PrayerTime(Prayer.Maghrib, local(p.maghrib)),
            PrayerTime(Prayer.Isha, isha),
        )
    }

    /** Degrees clockwise from true north. */
    fun qibla(latitude: Double, longitude: Double): Double = Qibla(Coordinates(latitude, longitude)).direction
}

object PrayerSchedule {
    /**
     * The next obligatory prayer after [now]. After Isha it is tomorrow's Fajr,
     * taken from [tomorrow] when given (otherwise today's Fajr time).
     */
    fun next(now: LocalDateTime, times: List<PrayerTime>, tomorrow: List<PrayerTime>? = null): NextPrayer {
        val obligatory = times.filter { it.prayer.isObligatory }.sortedBy { it.time }
        require(obligatory.isNotEmpty()) { "No prayer times" }
        val later = obligatory.firstOrNull { it.time.isAfter(now.toLocalTime()) }
        if (later != null) return NextPrayer(later.prayer, now.toLocalDate().atTime(later.time))
        val fajr = tomorrow?.firstOrNull { it.prayer == Prayer.Fajr } ?: obligatory.first()
        return NextPrayer(fajr.prayer, now.toLocalDate().plusDays(1).atTime(fajr.time))
    }

    /**
     * The obligatory prayer that most recently started (at or before [now]).
     * Before Fajr it is yesterday's Isha, taken from [yesterday] when given.
     */
    fun previous(now: LocalDateTime, times: List<PrayerTime>, yesterday: List<PrayerTime>? = null): NextPrayer {
        val obligatory = times.filter { it.prayer.isObligatory }.sortedBy { it.time }
        require(obligatory.isNotEmpty()) { "No prayer times" }
        val earlier = obligatory.lastOrNull { !it.time.isAfter(now.toLocalTime()) }
        if (earlier != null) return NextPrayer(earlier.prayer, now.toLocalDate().atTime(earlier.time))
        val isha = yesterday?.filter { it.prayer.isObligatory }?.maxByOrNull { it.time } ?: obligatory.last()
        return NextPrayer(isha.prayer, now.toLocalDate().minusDays(1).atTime(isha.time))
    }

    /** Share of the time between the previous and the next prayer that has passed (0..1). */
    fun progress(now: LocalDateTime, previous: LocalDateTime, next: LocalDateTime): Float {
        val total = java.time.Duration.between(previous, next).seconds.toFloat()
        if (total <= 0f) return 0f
        return (java.time.Duration.between(previous, now).seconds / total).coerceIn(0f, 1f)
    }
}
