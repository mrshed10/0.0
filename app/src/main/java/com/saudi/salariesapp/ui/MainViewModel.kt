package com.saudi.salariesapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saudi.salariesapp.data.AppPreferences
import com.saudi.salariesapp.data.CustomCountdown
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.EventsRepository
import com.saudi.salariesapp.data.Occasions
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.data.WidgetStyle
import com.saudi.salariesapp.widget.SalariesWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class ServiceUi(
    val id: String,
    val name: String,
    val day: Int,
    val next: EventModel,
    val schedule: List<EventModel>,
    val notify: Boolean,
    val pinned: Boolean,
)

data class UiState(
    val today: LocalDate = LocalDate.now(),
    val upcoming: List<EventModel> = emptyList(),
    val services: List<ServiceUi> = emptyList(),
    val occasions: List<EventModel> = emptyList(),
    val countdowns: List<CustomCountdown> = emptyList(),
    val pinned: Set<String> = emptySet(),
    val widgetStyle: WidgetStyle = WidgetStyle(),
    val widgetPreview: List<EventModel> = emptyList(),
    val themeMode: Int = 0,
)

/** ViewModel وحيد يغذّي كل الشاشات (MVVM): الواجهة تقرأ [state] وتستدعي الأفعال فقط. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo = EventsRepository(prefs)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val today = LocalDate.now()
        val pinned = prefs.widgetPinned()
        _state.value = UiState(
            today = today,
            upcoming = repo.allUpcoming(today),
            services = PaymentSchedule.services.map { s ->
                val schedule = repo.scheduleFor(s, 6, today)
                ServiceUi(
                    id = s.id,
                    name = s.name,
                    day = prefs.paymentDay(s),
                    next = schedule.first(),
                    schedule = schedule,
                    notify = prefs.isNotificationEnabled(s.id),
                    pinned = s.id in pinned,
                )
            },
            occasions = Occasions.upcoming(today),
            countdowns = prefs.loadCountdowns().sortedBy { it.target },
            pinned = pinned,
            widgetStyle = prefs.widgetStyle(),
            widgetPreview = repo.widgetEvents(today),
            themeMode = prefs.themeMode(),
        )
    }

    private fun changed(updateWidget: Boolean = true) {
        refresh()
        if (updateWidget) viewModelScope.launch { SalariesWidget.refreshAll(getApplication()) }
    }

    fun addCountdown(c: CustomCountdown) {
        val withId = if (c.id.isBlank()) c.copy(id = "cd_" + UUID.randomUUID()) else c
        prefs.saveCountdowns(prefs.loadCountdowns().filterNot { it.id == withId.id } + withId)
        changed()
    }

    fun deleteCountdown(id: String) {
        prefs.saveCountdowns(prefs.loadCountdowns().filterNot { it.id == id })
        prefs.setWidgetPinned(id, false)
        changed()
    }

    fun setPaymentDay(serviceId: String, day: Int) {
        prefs.setPaymentDay(serviceId, day)
        changed()
    }

    fun setNotify(serviceId: String, enabled: Boolean) {
        prefs.setNotificationEnabled(serviceId, enabled)
        changed(updateWidget = false)
    }

    fun setPinned(id: String, pinned: Boolean) {
        prefs.setWidgetPinned(id, pinned)
        changed()
    }

    fun saveWidgetStyle(style: WidgetStyle) {
        prefs.saveWidgetStyle(style)
        changed()
    }

    fun setThemeMode(mode: Int) {
        prefs.setThemeMode(mode)
        changed(updateWidget = false)
    }
}
