package com.athar.app.feature.dates

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.R
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.GeneralPrefs
import com.athar.app.core.data.PlannerRepository
import com.athar.app.core.data.appContainer
import com.athar.app.core.data.atharApplication
import com.athar.app.core.region.Country
import com.athar.app.feature.reminders.Reminder
import com.athar.app.feature.reminders.ReminderMath
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.salary.SalarySource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class SalaryRow(val source: SalarySource, val upcoming: List<LocalDate>) {
    val next: LocalDate get() = upcoming.first()
}

data class ReminderRow(val reminder: Reminder, val next: LocalDateTime?)

data class PlannerUiState(
    val now: LocalDateTime = LocalDateTime.now(),
    val general: GeneralPrefs? = null,
    val salaries: List<SalaryRow> = emptyList(),
    val reminders: List<ReminderRow> = emptyList(),
    val loaded: Boolean = false,
)

class PlannerViewModel(
    private val repository: PlannerRepository,
    private val settings: AppSettings,
    private val app: Application,
) : ViewModel() {

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    val uiState: StateFlow<PlannerUiState> =
        combine(clock, settings.general, repository.salaries, repository.reminders) { now, general, salaries, reminders ->
            val today = now.toLocalDate()
            PlannerUiState(
                now = now,
                general = general,
                salaries = salaries
                    .map { SalaryRow(it, SalarySchedule.upcoming(it, today, general.country.weekend, count = 4)) }
                    .sortedBy { it.next },
                reminders = reminders
                    .map { ReminderRow(it, if (it.enabled) ReminderMath.next(it, now) else null) }
                    .sortedWith(compareBy<ReminderRow> { it.next == null }.thenBy { it.next }),
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    init {
        // First run in Saudi Arabia: add the common payment dates (editable estimates).
        if (!settings.salariesSeeded && settings.general.value.country == Country.SA) {
            viewModelScope.launch {
                repository.seedIfEmpty(saudiPresets())
                settings.salariesSeeded = true
            }
        }
    }

    private fun saudiPresets(): List<SalarySource> {
        fun s(name: Int, day: Int, remind: Int = -1) = SalarySource(name = app.getString(name), payDay = day, remindDaysBefore = remind)
        return listOf(
            s(R.string.preset_employees, 27, remind = 1),
            s(R.string.preset_retirement, 1),
            s(R.string.preset_citizen_account, 10),
            s(R.string.preset_social_security, 1),
            s(R.string.preset_sakani, 24),
            s(R.string.preset_rehab, 5),
        )
    }

    fun setCountry(country: Country) = settings.setCountry(country)
    fun setSalaryTone(tone: String?) = settings.updateGeneral { it.copy(salaryTone = tone) }
    fun setReminderTone(tone: String?) = settings.updateGeneral { it.copy(reminderTone = tone) }

    fun saveSalary(source: SalarySource) {
        viewModelScope.launch { repository.saveSalary(source) }
    }

    fun deleteSalary(id: Long) {
        viewModelScope.launch { repository.deleteSalary(id) }
    }

    fun saveReminder(reminder: Reminder) {
        viewModelScope.launch { repository.saveReminder(reminder) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                PlannerViewModel(c.plannerRepository, c.settings, atharApplication())
            }
        }
    }
}
