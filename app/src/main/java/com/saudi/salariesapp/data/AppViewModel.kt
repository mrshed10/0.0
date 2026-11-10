package com.saudi.salariesapp.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saudi.salariesapp.reminders.ReminderScheduler
import com.saudi.salariesapp.widget.SalariesWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPrefs(app)

    private val _countdowns = MutableStateFlow(prefs.loadCountdowns())
    val countdowns: StateFlow<List<EventModel>> = _countdowns.asStateFlow()

    private val _widget = MutableStateFlow(prefs.loadWidgetSettings())
    val widget: StateFlow<WidgetSettings> = _widget.asStateFlow()

    private val _reminders = MutableStateFlow(prefs.loadReminderIds())
    val reminders: StateFlow<Set<String>> = _reminders.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.loadDarkMode())
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    // ---- Countdowns -------------------------------------------------------------------------

    fun addCountdown(event: EventModel) {
        val withId = event.copy(id = "custom_" + UUID.randomUUID(), isCustom = true, category = Category.CUSTOM)
        _countdowns.update { (it + withId).sortedBy { e -> e.target } }
        prefs.saveCountdowns(_countdowns.value)
        refreshWidget()
    }

    fun deleteCountdown(id: String) {
        _countdowns.update { list -> list.filterNot { it.id == id } }
        prefs.saveCountdowns(_countdowns.value)
        if (id in _reminders.value) toggleReminder(id)
        if (id in _widget.value.pinnedIds) toggleWidgetPin(id)
        refreshWidget()
    }

    // ---- Widget -----------------------------------------------------------------------------

    fun toggleWidgetPin(id: String) {
        updateWidget { s ->
            s.copy(pinnedIds = if (id in s.pinnedIds) s.pinnedIds - id else s.pinnedIds + id)
        }
    }

    fun updateWidget(transform: (WidgetSettings) -> WidgetSettings) {
        _widget.update(transform)
        prefs.saveWidgetSettings(_widget.value)
        refreshWidget()
    }

    private fun refreshWidget() {
        viewModelScope.launch { SalariesWidget.refreshAll(getApplication()) }
    }

    // ---- Reminders --------------------------------------------------------------------------

    fun toggleReminder(id: String) {
        _reminders.update { if (id in it) it - id else it + id }
        prefs.saveReminderIds(_reminders.value)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    // ---- Theme ------------------------------------------------------------------------------

    fun setDarkMode(value: Boolean?) {
        _darkMode.value = value
        prefs.saveDarkMode(value)
    }
}
