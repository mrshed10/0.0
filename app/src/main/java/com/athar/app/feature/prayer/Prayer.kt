package com.athar.app.feature.prayer

import androidx.annotation.StringRes
import com.athar.app.R
import java.time.LocalDateTime
import java.time.LocalTime

enum class Prayer(@StringRes val label: Int, val isObligatory: Boolean) {
    Fajr(R.string.prayer_fajr, true),
    Sunrise(R.string.prayer_sunrise, false),
    Dhuhr(R.string.prayer_dhuhr, true),
    Asr(R.string.prayer_asr, true),
    Maghrib(R.string.prayer_maghrib, true),
    Isha(R.string.prayer_isha, true),
}

data class PrayerTime(val prayer: Prayer, val time: LocalTime)

data class NextPrayer(val prayer: Prayer, val at: LocalDateTime)

object PrayerSchedule {
    /**
     * The next obligatory prayer after [now]; after Isha it is tomorrow's Fajr.
     * (Uses today's times for tomorrow; the real calculator will supply them.)
     */
    fun next(now: LocalDateTime, times: List<PrayerTime>): NextPrayer {
        val obligatory = times.filter { it.prayer.isObligatory }.sortedBy { it.time }
        require(obligatory.isNotEmpty()) { "No prayer times" }
        val later = obligatory.firstOrNull { it.time.isAfter(now.toLocalTime()) }
        return if (later != null) {
            NextPrayer(later.prayer, now.toLocalDate().atTime(later.time))
        } else {
            val first = obligatory.first()
            NextPrayer(first.prayer, now.toLocalDate().plusDays(1).atTime(first.time))
        }
    }
}
