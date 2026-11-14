package com.athar.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.CounterRepository
import com.athar.app.core.data.DebtRepository
import com.athar.app.core.data.HabitRepository
import com.athar.app.core.data.TaskRepository
import com.athar.app.core.data.appContainer
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.debts.DebtMath
import com.athar.app.feature.habits.StreakMath
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
    private val habitRepository: HabitRepository,
    debtRepository: DebtRepository,
) : ViewModel() {

    /** Clock + sections that are still sample/in-memory (prayer times, tasbih). */
    private val local = MutableStateFlow(SampleData.homeState(LocalDateTime.now()))

    private val plans = combine(counterRepository.counters, taskRepository.tasks) { c, t -> c to t }
    private val habits = combine(habitRepository.habits, habitRepository.doneDays) { h, d -> h to d }
    private val ledger = combine(debtRepository.people, debtRepository.entries) { p, e -> DebtMath.balances(p, e) }

    val uiState: StateFlow<HomeUiState> =
        combine(local, plans, habits, ledger) { state, (counters, tasks), (habitList, days), balances ->
            val today = state.today
            state.copy(
                counters = CounterMath.sort(counters, today),
                todayTasks = TaskGrouping.forToday(tasks, today),
                habits = habitList.map { StreakMath.summarize(it, days[it.id].orEmpty(), today) },
                debts = DebtMath.totals(balances),
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

    fun toggleHabit(id: Long) {
        viewModelScope.launch { habitRepository.toggle(id, LocalDate.now()) }
    }

    fun incrementTasbih() = local.update { it.copy(tasbihCount = it.tasbihCount + 1) }

    fun resetTasbih() = local.update { it.copy(tasbihCount = 0) }

    companion object {
        private const val TICK_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                HomeViewModel(c.counterRepository, c.taskRepository, c.habitRepository, c.debtRepository)
            }
        }
    }
}
