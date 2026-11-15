package com.athar.app.feature.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.PrayerPrefs
import com.athar.app.core.data.TasbihState
import com.athar.app.core.data.appContainer
import com.athar.app.core.data.tapped
import com.athar.app.core.region.Cities
import com.athar.app.core.region.City
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

data class PrayerUiState(
    val now: LocalDateTime,
    val prefs: PrayerPrefs,
    val times: List<PrayerTime>,
    val next: NextPrayer,
    val qibla: Double,
    val city: City?,
    val tasbih: TasbihState,
    /** The prayer whose time we are in (elapsed, time left, iqama). */
    val current: CurrentPrayer?,
)

class PrayerViewModel(private val settings: AppSettings) : ViewModel() {

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000)
        }
    }

    val uiState: StateFlow<PrayerUiState> =
        combine(clock, settings.prayer, settings.tasbih) { now, prefs, tasbih -> build(now, prefs, tasbih) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                build(LocalDateTime.now(), settings.prayer.value, settings.tasbih.value),
            )

    private fun build(now: LocalDateTime, prefs: PrayerPrefs, tasbih: TasbihState): PrayerUiState {
        val today = prefs.timesFor(now.toLocalDate())
        val tomorrow = prefs.timesFor(now.toLocalDate().plusDays(1))
        val yesterday = prefs.timesFor(now.toLocalDate().minusDays(1))
        return PrayerUiState(
            now = now,
            prefs = prefs,
            times = today,
            next = PrayerSchedule.next(now, today, tomorrow),
            qibla = PrayerCalculator.qibla(prefs.latitude, prefs.longitude),
            city = Cities.byId(prefs.cityId),
            tasbih = tasbih,
            current = PrayerWindow.current(now, yesterday, today, tomorrow) { p ->
                prefs.alert(p).takeIf { it.iqamaEnabled }?.iqamaMinutes
            },
        )
    }

    fun selectCity(city: City) = settings.updatePrayer {
        it.copy(latitude = city.latitude, longitude = city.longitude, cityId = city.id)
    }

    fun useLocation(latitude: Double, longitude: Double) = settings.updatePrayer {
        it.copy(latitude = latitude, longitude = longitude, cityId = Cities.nearest(latitude, longitude)?.id)
    }

    fun setMethod(method: PrayerMethod) = settings.updatePrayer { it.copy(method = method) }
    fun setMadhab(madhab: AsrMadhab) = settings.updatePrayer { it.copy(madhab = madhab) }
    fun setFajrTone(tone: String?) = settings.updatePrayer { it.copy(fajrTone = tone) }
    fun setAdhanTone(tone: String?) = settings.updatePrayer { it.copy(adhanTone = tone) }

    fun toggleAdhan(prayer: Prayer) = settings.updatePrayer {
        val a = it.alert(prayer)
        it.copy(alerts = it.alerts + (prayer to a.copy(adhan = !a.adhan)))
    }

    /** Saves one prayer's settings and whether the phone goes silent for it. */
    fun saveAlert(prayer: Prayer, alert: PrayerAlert, silentForPrayer: Boolean) = settings.updatePrayer {
        val prayers = if (silentForPrayer) it.silent.prayers + prayer else it.silent.prayers - prayer
        it.copy(alerts = it.alerts + (prayer to alert), silent = it.silent.copy(prayers = prayers))
    }

    /** Copies [alert] to all prayers, keeping each prayer's own iqama time. */
    fun applyToAll(alert: PrayerAlert, silentForPrayer: Boolean) = settings.updatePrayer { p ->
        val obligatory = Prayer.entries.filter { it.isObligatory }
        p.copy(
            alerts = obligatory.associateWith { alert.copy(iqamaMinutes = p.alert(it).iqamaMinutes) },
            silent = p.silent.copy(prayers = if (silentForPrayer) obligatory.toSet() else emptySet()),
        )
    }

    fun setIqamaMinutes(prayer: Prayer, minutes: Int) = settings.updatePrayer {
        it.copy(alerts = it.alerts + (prayer to it.alert(prayer).copy(iqamaMinutes = minutes.coerceIn(0, 90))))
    }

    fun setVolume(volume: AdhanVolume) = settings.updatePrayer { it.copy(volume = volume) }
    fun setAdhanInSilent(enabled: Boolean) = settings.updatePrayer { it.copy(adhanInSilent = enabled) }
    fun updateSilent(transform: (SilentPrefs) -> SilentPrefs) = settings.updatePrayer { it.copy(silent = transform(it.silent)) }
    fun resetDefaults() = settings.resetPrayerAlerts()

    // --- tasbih ---
    fun tasbihTap() = settings.updateTasbih { it.tapped(LocalDate.now()) }
    fun tasbihReset() = settings.updateTasbih { it.copy(count = 0) }
    fun tasbihPreset(index: Int) = settings.updateTasbih { it.copy(presetIndex = index, count = 0) }
    fun tasbihCustom(text: String, target: Int) = settings.updateTasbih {
        it.copy(presetIndex = SpiritualContent.tasbihPresets.size, customText = text, customTarget = target.coerceIn(1, 10_000), count = 0)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PrayerViewModel(appContainer().settings) }
        }
    }
}
