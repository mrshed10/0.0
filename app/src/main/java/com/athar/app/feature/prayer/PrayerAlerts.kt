package com.athar.app.feature.prayer

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Settings for one prayer: adhan, alerts before it, iqama after it. */
data class PrayerAlert(
    val adhan: Boolean = true,
    val preEnabled: Boolean = false,
    /** First pre-alert this many minutes before the adhan. */
    val preMinutes: Int = 10,
    /** How many pre-alerts, spread evenly until the adhan (1–3). */
    val preCount: Int = 1,
    val preTone: String? = null,
    /** Show the countdown to iqama after the adhan. */
    val iqamaEnabled: Boolean = true,
    val iqamaMinutes: Int = 20,
    /** Notify (with a tone) when it is time for iqama. */
    val iqamaAlert: Boolean = false,
    val iqamaTone: String? = null,
) {
    /** Minutes before the adhan for each pre-alert, e.g. 15 min × 3 → [15, 10, 5]. */
    fun preOffsets(): List<Int> {
        if (!preEnabled || preMinutes <= 0) return emptyList()
        val count = preCount.coerceIn(1, 3)
        val step = preMinutes / count
        return (0 until count).map { preMinutes - it * step }.filter { it > 0 }.distinct()
    }

    companion object {
        /** Common Saudi iqama intervals. */
        fun default(prayer: Prayer) = PrayerAlert(
            iqamaMinutes = when (prayer) {
                Prayer.Fajr -> 25
                Prayer.Maghrib -> 10
                else -> 20
            },
        )
    }
}

/**
 * Spoken announcements (phone text-to-speech): before the prayer ("Dhuhr is
 * near, 10 minutes left"), at the adhan ("Allahu Akbar, it is time for Dhuhr")
 * and at iqama ("Hayya 'ala as-salah").
 */
data class VoicePrefs(
    val pre: Boolean = true,
    val adhan: Boolean = true,
    val iqama: Boolean = true,
)

object IqamaMath {
    /** Iqama time after an adhan, or null if the iqama countdown is off for that prayer. */
    fun iqamaAt(adhan: NextPrayer, alert: PrayerAlert): LocalDateTime? =
        if (alert.iqamaEnabled && alert.iqamaMinutes > 0) adhan.at.plusMinutes(alert.iqamaMinutes.toLong()) else null
}

/** How loud the adhan plays. */
enum class AdhanVolume(val factor: Float) { Alarm(1f), Medium(0.6f), Low(0.3f), Silent(0f) }

enum class SilentMode { Silent, Vibrate }

/** Put the phone on silent/vibrate during prayers. */
data class SilentPrefs(
    val enabled: Boolean = false,
    val prayers: Set<Prayer> = Prayer.entries.filter { it.isObligatory }.toSet(),
    val mode: SilentMode = SilentMode.Silent,
    /** How long the phone stays silent from iqama. */
    val durationMinutes: Int = 20,
    /** On Fridays Dhuhr is replaced by Jumu'ah with its own timing. */
    val jumuah: Boolean = true,
    val jumuahStartAfter: Int = 0,
    val jumuahDuration: Int = 60,
)

/** A silent window (start = iqama, or Jumu'ah adhan + offset). */
data class SilentWindow(val prayer: Prayer, val start: LocalDateTime, val end: LocalDateTime, val jumuah: Boolean)

object PrayerAlertMath {
    fun silentWindow(
        prayer: Prayer,
        adhan: LocalDateTime,
        alert: PrayerAlert,
        silent: SilentPrefs,
    ): SilentWindow? {
        if (!silent.enabled) return null
        val isJumuah = prayer == Prayer.Dhuhr && adhan.dayOfWeek == java.time.DayOfWeek.FRIDAY && silent.jumuah
        if (!isJumuah && prayer !in silent.prayers) return null
        val start = if (isJumuah) adhan.plusMinutes(silent.jumuahStartAfter.toLong()) else adhan.plusMinutes(alert.iqamaMinutes.toLong())
        val duration = if (isJumuah) silent.jumuahDuration else silent.durationMinutes
        if (duration <= 0) return null
        return SilentWindow(prayer, start, start.plusMinutes(duration.toLong()), isJumuah)
    }
}

/** The prayer whose time we are in now, with its end and iqama. */
data class CurrentPrayer(
    val prayer: Prayer,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val iqamaAt: LocalDateTime?,
) {
    fun elapsed(now: LocalDateTime): Duration = Duration.between(start, now)
    fun left(now: LocalDateTime): Duration = Duration.between(now, end)
}

object PrayerWindow {
    /**
     * Prayer-time windows: Fajr→sunrise, Dhuhr→Asr, Asr→Maghrib, Maghrib→Isha,
     * Isha→Islamic midnight (half way from Maghrib to the next Fajr).
     * Returns null between sunrise and Dhuhr, or after midnight before Fajr.
     */
    fun current(
        now: LocalDateTime,
        yesterday: List<PrayerTime>,
        today: List<PrayerTime>,
        tomorrow: List<PrayerTime>,
        iqamaMinutes: (Prayer) -> Int?,
    ): CurrentPrayer? {
        val d = now.toLocalDate()
        fun at(day: LocalDate, list: List<PrayerTime>, p: Prayer): LocalDateTime? =
            list.firstOrNull { it.prayer == p }?.let { day.atTime(it.time) }

        val y = d.minusDays(1)
        val t = d.plusDays(1)
        fun midnight(maghrib: LocalDateTime?, fajr: LocalDateTime?): LocalDateTime? =
            if (maghrib == null || fajr == null) null else maghrib.plus(Duration.between(maghrib, fajr).dividedBy(2))

        val windows = listOf(
            Triple(Prayer.Isha, at(y, yesterday, Prayer.Isha), midnight(at(y, yesterday, Prayer.Maghrib), at(d, today, Prayer.Fajr))),
            Triple(Prayer.Fajr, at(d, today, Prayer.Fajr), at(d, today, Prayer.Sunrise)),
            Triple(Prayer.Dhuhr, at(d, today, Prayer.Dhuhr), at(d, today, Prayer.Asr)),
            Triple(Prayer.Asr, at(d, today, Prayer.Asr), at(d, today, Prayer.Maghrib)),
            Triple(Prayer.Maghrib, at(d, today, Prayer.Maghrib), at(d, today, Prayer.Isha)),
            Triple(Prayer.Isha, at(d, today, Prayer.Isha), midnight(at(d, today, Prayer.Maghrib), at(t, tomorrow, Prayer.Fajr))),
        )
        return windows.firstNotNullOfOrNull { (prayer, start, end) ->
            if (start == null || end == null || now.isBefore(start) || !now.isBefore(end)) {
                null
            } else {
                CurrentPrayer(prayer, start, end, iqamaMinutes(prayer)?.let { start.plusMinutes(it.toLong()) })
            }
        }
    }
}

object PrayerAlertsJson {
    fun encodeAlerts(alerts: Map<Prayer, PrayerAlert>): String = JSONObject().apply {
        alerts.forEach { (p, a) ->
            put(
                p.name,
                JSONObject()
                    .put("adhan", a.adhan)
                    .put("pre", a.preEnabled).put("preMin", a.preMinutes).put("preCount", a.preCount)
                    .put("preTone", a.preTone ?: JSONObject.NULL)
                    .put("iqama", a.iqamaEnabled).put("iqamaMin", a.iqamaMinutes)
                    .put("iqamaAlert", a.iqamaAlert).put("iqamaTone", a.iqamaTone ?: JSONObject.NULL),
            )
        }
    }.toString()

    fun decodeAlerts(raw: String?): Map<Prayer, PrayerAlert>? = runCatching {
        val o = JSONObject(raw ?: return null)
        Prayer.entries.filter { it.isObligatory }.associateWith { p ->
            val a = o.optJSONObject(p.name) ?: return@associateWith PrayerAlert.default(p)
            val d = PrayerAlert.default(p)
            PrayerAlert(
                adhan = a.optBoolean("adhan", d.adhan),
                preEnabled = a.optBoolean("pre", d.preEnabled),
                preMinutes = a.optInt("preMin", d.preMinutes).coerceIn(1, 120),
                preCount = a.optInt("preCount", d.preCount).coerceIn(1, 3),
                preTone = if (a.isNull("preTone")) null else a.optString("preTone"),
                iqamaEnabled = a.optBoolean("iqama", d.iqamaEnabled),
                iqamaMinutes = a.optInt("iqamaMin", d.iqamaMinutes).coerceIn(0, 90),
                iqamaAlert = a.optBoolean("iqamaAlert", d.iqamaAlert),
                iqamaTone = if (a.isNull("iqamaTone")) null else a.optString("iqamaTone"),
            )
        }
    }.getOrNull()

    fun encodeSilent(s: SilentPrefs): String = JSONObject()
        .put("enabled", s.enabled)
        .put("prayers", JSONArray(s.prayers.map { it.name }))
        .put("mode", s.mode.name)
        .put("duration", s.durationMinutes)
        .put("jumuah", s.jumuah).put("jumuahStart", s.jumuahStartAfter).put("jumuahDuration", s.jumuahDuration)
        .toString()

    fun decodeSilent(raw: String?): SilentPrefs? = runCatching {
        val o = JSONObject(raw ?: return null)
        val d = SilentPrefs()
        val names = o.optJSONArray("prayers")
        SilentPrefs(
            enabled = o.optBoolean("enabled", d.enabled),
            prayers = if (names == null) d.prayers else (0 until names.length()).mapNotNull { i ->
                Prayer.entries.firstOrNull { it.name == names.getString(i) }
            }.toSet(),
            mode = SilentMode.entries.firstOrNull { it.name == o.optString("mode") } ?: d.mode,
            durationMinutes = o.optInt("duration", d.durationMinutes).coerceIn(5, 180),
            jumuah = o.optBoolean("jumuah", d.jumuah),
            jumuahStartAfter = o.optInt("jumuahStart", d.jumuahStartAfter).coerceIn(0, 120),
            jumuahDuration = o.optInt("jumuahDuration", d.jumuahDuration).coerceIn(5, 180),
        )
    }.getOrNull()
}
