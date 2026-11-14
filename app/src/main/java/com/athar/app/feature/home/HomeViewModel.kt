package com.athar.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SampleData.homeState(LocalDateTime.now()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Keep "next prayer in …" and the day counters current.
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                _uiState.update { it.copy(now = LocalDateTime.now()) }
            }
        }
    }

    fun toggleTask(id: Long) = _uiState.update { state ->
        state.copy(tasks = state.tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun toggleHabit(id: Long) = _uiState.update { state ->
        state.copy(
            habits = state.habits.map {
                if (it.id != id) {
                    it
                } else {
                    val done = !it.doneToday
                    it.copy(doneToday = done, streak = (it.streak + if (done) 1 else -1).coerceAtLeast(0))
                }
            },
        )
    }

    fun incrementTasbih() = _uiState.update { it.copy(tasbihCount = it.tasbihCount + 1) }

    fun resetTasbih() = _uiState.update { it.copy(tasbihCount = 0) }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}
