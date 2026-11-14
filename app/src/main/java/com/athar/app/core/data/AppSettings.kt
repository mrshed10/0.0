package com.athar.app.core.data

import android.content.Context
import android.content.SharedPreferences
import com.athar.app.core.region.Cities
import com.athar.app.core.region.Country
import com.athar.app.feature.prayer.AsrMadhab
import com.athar.app.feature.prayer.Prayer
import com.athar.app.feature.prayer.PrayerCalculator
import com.athar.app.feature.prayer.PrayerMethod
import com.athar.app.feature.prayer.PrayerTime
import com.athar.app.widget.WidgetContent
import com.athar.app.widget.WidgetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

data class PrayerPrefs(
    val latitude: Double,
    val longitude: Double,
    /** Built-in city id, or null for a GPS position. */
    val cityId: String?,
    val method: PrayerMethod,
    val madhab: AsrMadhab,
    val adhanEnabled: Set<Prayer>,
    /** Minutes before each prayer for an extra reminder; 0 = off. */
    val preMinutes: Int,
    /** Notification sound URIs; null = system default. */
    val fajrTone: String?,
    val adhanTone: String?,
) {
    fun timesFor(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<PrayerTime> =
        PrayerCalculator.times(date, latitude, longitude, method, madhab, zone)
}

data class GeneralPrefs(
    val country: Country,
    val salaryTone: String?,
    val reminderTone: String?,
)

data class TasbihState(
    val presetIndex: Int = 0,
    val customText: String = "",
    val customTarget: Int = 33,
    val count: Int = 0,
    val todayTotal: Int = 0,
    val todayEpochDay: Long = 0,
    val lifetime: Long = 0,
)

/** Small key-value preferences, exposed as flows. */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("athar_settings", Context.MODE_PRIVATE)

    // --- debts lock ---
    private val _debtsLock = MutableStateFlow(prefs.getBoolean(KEY_DEBTS_LOCK, false))
    val debtsLock: StateFlow<Boolean> = _debtsLock.asStateFlow()

    fun setDebtsLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBTS_LOCK, enabled).apply()
        _debtsLock.value = enabled
    }

    // --- general ---
    private val _general = MutableStateFlow(readGeneral())
    val general: StateFlow<GeneralPrefs> = _general.asStateFlow()

    fun updateGeneral(transform: (GeneralPrefs) -> GeneralPrefs) {
        val value = transform(_general.value)
        prefs.edit()
            .putString("country", value.country.name)
            .putString("salary_tone", value.salaryTone)
            .putString("reminder_tone", value.reminderTone)
            .apply()
        _general.value = value
    }

    /** Changes country and adopts its capital and prayer method. */
    fun setCountry(country: Country) {
        updateGeneral { it.copy(country = country) }
        val city = Cities.byId(country.capitalCityId)
        updatePrayer {
            it.copy(
                latitude = city?.latitude ?: it.latitude,
                longitude = city?.longitude ?: it.longitude,
                cityId = city?.id ?: it.cityId,
                method = country.prayerMethod,
            )
        }
    }

    private fun readGeneral() = GeneralPrefs(
        country = enumOr(prefs.getString("country", null), Country.SA),
        salaryTone = prefs.getString("salary_tone", null),
        reminderTone = prefs.getString("reminder_tone", null),
    )

    // --- prayer ---
    private val _prayer = MutableStateFlow(readPrayer())
    val prayer: StateFlow<PrayerPrefs> = _prayer.asStateFlow()

    fun updatePrayer(transform: (PrayerPrefs) -> PrayerPrefs) {
        val value = transform(_prayer.value)
        prefs.edit()
            .putString("prayer_lat", value.latitude.toString())
            .putString("prayer_lon", value.longitude.toString())
            .putString("prayer_city", value.cityId)
            .putString("prayer_method", value.method.name)
            .putString("prayer_madhab", value.madhab.name)
            .putStringSet("prayer_adhan", value.adhanEnabled.map { it.name }.toSet())
            .putInt("prayer_pre", value.preMinutes)
            .putString("tone_fajr", value.fajrTone)
            .putString("tone_adhan", value.adhanTone)
            .apply()
        _prayer.value = value
    }

    private fun readPrayer(): PrayerPrefs {
        val riyadh = Cities.byId("riyadh")!!
        return PrayerPrefs(
            latitude = prefs.getString("prayer_lat", null)?.toDoubleOrNull() ?: riyadh.latitude,
            longitude = prefs.getString("prayer_lon", null)?.toDoubleOrNull() ?: riyadh.longitude,
            cityId = if (prefs.contains("prayer_lat")) prefs.getString("prayer_city", null) else riyadh.id,
            method = enumOr(prefs.getString("prayer_method", null), PrayerMethod.UmmAlQura),
            madhab = enumOr(prefs.getString("prayer_madhab", null), AsrMadhab.Standard),
            adhanEnabled = prefs.getStringSet("prayer_adhan", null)
                ?.mapNotNull { name -> Prayer.entries.firstOrNull { it.name == name } }?.toSet()
                ?: Prayer.entries.filter { it.isObligatory }.toSet(),
            preMinutes = prefs.getInt("prayer_pre", 0),
            fajrTone = prefs.getString("tone_fajr", null),
            adhanTone = prefs.getString("tone_adhan", null),
        )
    }

    // --- tasbih ---
    private val _tasbih = MutableStateFlow(readTasbih())
    val tasbih: StateFlow<TasbihState> = _tasbih.asStateFlow()

    fun updateTasbih(transform: (TasbihState) -> TasbihState) {
        val value = transform(_tasbih.value)
        prefs.edit()
            .putInt("tasbih_preset", value.presetIndex)
            .putString("tasbih_custom", value.customText)
            .putInt("tasbih_custom_target", value.customTarget)
            .putInt("tasbih_count", value.count)
            .putInt("tasbih_today", value.todayTotal)
            .putLong("tasbih_day", value.todayEpochDay)
            .putLong("tasbih_lifetime", value.lifetime)
            .apply()
        _tasbih.value = value
    }

    private fun readTasbih() = TasbihState(
        presetIndex = prefs.getInt("tasbih_preset", 0),
        customText = prefs.getString("tasbih_custom", "") ?: "",
        customTarget = prefs.getInt("tasbih_custom_target", 33),
        count = prefs.getInt("tasbih_count", 0),
        todayTotal = prefs.getInt("tasbih_today", 0),
        todayEpochDay = prefs.getLong("tasbih_day", 0),
        lifetime = prefs.getLong("tasbih_lifetime", 0),
    )

    // --- lock-screen card ---
    private val _lockCard = MutableStateFlow(readLockCard())
    /** What the lock-screen card shows; null = off. */
    val lockCard: StateFlow<WidgetContent?> = _lockCard.asStateFlow()

    fun setLockCard(content: WidgetContent?) {
        prefs.edit()
            .putString("lock_type", content?.type?.name)
            .putLong("lock_item", content?.itemId ?: -1)
            .apply()
        _lockCard.value = content
    }

    private fun readLockCard(): WidgetContent? {
        val type = WidgetType.entries.firstOrNull { it.name == prefs.getString("lock_type", null) } ?: return null
        return WidgetContent(type, prefs.getLong("lock_item", -1))
    }

    // --- one-off flags ---
    var salariesSeeded: Boolean
        get() = prefs.getBoolean("salaries_seeded", false)
        set(value) = prefs.edit().putBoolean("salaries_seeded", value).apply()

    /** Alarm request codes currently scheduled (to cancel stale ones). */
    var scheduledAlarmCodes: Set<Int>
        get() = prefs.getStringSet("alarm_codes", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = prefs.edit().putStringSet("alarm_codes", value.map { it.toString() }.toSet()).apply()

    private companion object {
        const val KEY_DEBTS_LOCK = "debts_lock"
    }
}

/** One tasbih tap: counts toward the current round, today and lifetime totals. */
fun TasbihState.tapped(today: LocalDate): TasbihState {
    val day = today.toEpochDay()
    val todaySoFar = if (todayEpochDay == day) todayTotal else 0
    return copy(count = count + 1, todayTotal = todaySoFar + 1, todayEpochDay = day, lifetime = lifetime + 1)
}

fun TasbihState.todayCount(today: LocalDate): Int = if (todayEpochDay == today.toEpochDay()) todayTotal else 0

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

/** In-memory unlock state for the debts section; re-locks when the app goes to background. */
class DebtsSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}
