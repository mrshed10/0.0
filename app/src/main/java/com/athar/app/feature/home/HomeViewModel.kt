package com.athar.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.CounterRepository
import com.athar.app.core.data.TaskRepository
import com.athar.app.core.data.appContainer
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.tasks.Task
import com.athar.app.feature.tasks.TaskGrouping
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class HomeViewModel(
    private val counterRepository: CounterRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    /** Clock + sections that are still sample/in-memory (prayer, habits, debts, tasbih). */
    private val local = MutableStateFlow(SampleData.homeState(LocalDateTime.now()))

    val uiState: StateFlow<HomeUiState> =
        combine(local, counterRepository.counters, taskRepository.tasks) { state, counters, tasks ->
            state.copy(
                counters = CounterMath.sort(counters, state.today),
                todayTasks = TaskGrouping.forToday(tasks, state.today),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Keep "next prayer in …" and the day counters current.
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                local.update { it.copy(now = LocalDateTime.now()) }
            }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskRepository.setDone(task.id, !task.done, LocalDate.now()) }
    }

    fun toggleHabit(id: Long) = local.update { state ->
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

    fun incrementTasbih() = local.update { it.copy(tasbihCount = it.tasbihCount + 1) }

    fun resetTasbih() = local.update { it.copy(tasbihCount = 0) }

    companion object {
        private const val TICK_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.counterRepository, container.taskRepository)
            }
        }
    }
}
