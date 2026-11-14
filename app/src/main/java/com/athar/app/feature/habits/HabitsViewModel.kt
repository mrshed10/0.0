package com.athar.app.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.HabitRepository
import com.athar.app.core.data.appContainer
import com.athar.app.core.time.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitsUiState(
    val today: LocalDate = LocalDate.now(),
    val habits: List<HabitSummary> = emptyList(),
    val loaded: Boolean = false,
) {
    val doneToday: Int get() = habits.count { it.doneToday }
}

class HabitsViewModel(private val repository: HabitRepository) : ViewModel() {

    val uiState: StateFlow<HabitsUiState> =
        combine(repository.habits, repository.doneDays, todayFlow()) { habits, days, today ->
            HabitsUiState(
                today = today,
                habits = habits.map { StreakMath.summarize(it, days[it.id].orEmpty(), today) },
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggle(habitId: Long, day: LocalDate) {
        viewModelScope.launch { repository.toggle(habitId, day) }
    }

    fun save(habit: Habit) {
        viewModelScope.launch { repository.save(habit) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HabitsViewModel(appContainer().habitRepository) }
        }
    }
}
