package com.athar.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.CounterRepository
import com.athar.app.core.data.DebtRepository
import com.athar.app.core.data.HabitRepository
import com.athar.app.core.data.PlannerRepository
import com.athar.app.core.data.TaskRepository
import com.athar.app.core.data.appContainer
import com.athar.app.core.data.tapped
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.debts.DebtMath
import com.athar.app.feature.habits.StreakMath
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.tasks.Task
import com.athar.app.feature.tasks.TaskGrouping
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class HomeViewModel(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val settings: AppSettings,
    counterRepository: CounterRepository,
    debtRepository: DebtRepository,
    plannerRepository: PlannerRepository,
) : ViewModel() {

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MILLIS)
        }
    }

    private val plans = combine(counterRepository.counters, taskRepository.tasks) { c, t -> c to t }
    private val habits = combine(habitRepository.habits, habitRepository.doneDays) { h, d -> h to d }
    private val ledger = combine(debtRepository.people, debtRepository.entries) { p, e -> DebtMath.balances(p, e) }
    private val spiritual = combine(settings.prayer, settings.tasbih, settings.general, plannerRepository.salaries) { p, t, g, s ->
        Spiritual(p, t, g, s)
    }

    private data class Spiritual(
        val prayer: com.athar.app.core.data.PrayerPrefs,
        val tasbih: com.athar.app.core.data.TasbihState,
        val general: com.athar.app.core.data.GeneralPrefs,
        val salaries: List<com.athar.app.feature.salary.SalarySource>,
    )

    val uiState: StateFlow<HomeUiState> =
        combine(clock, plans, habits, ledger, spiritual) { now, (counters, tasks), (habitList, days), balances, sp ->
            val today = now.toLocalDate()
            HomeUiState(
                now = now,
                prayers = sp.prayer.timesFor(today),
                tomorrowPrayers = sp.prayer.timesFor(today.plusDays(1)),
                cityId = sp.prayer.cityId,
                counters = CounterMath.sort(counters, today),
                todayTasks = TaskGrouping.forToday(tasks, today),
                habits = habitList.map { StreakMath.summarize(it, days[it.id].orEmpty(), today) },
                debts = DebtMath.totals(balances),
                tasbih = sp.tasbih,
                nextSalary = sp.salaries
                    .map { NextSalary(it.name, SalarySchedule.next(it, today, sp.general.country.weekend)) }
                    .minByOrNull { it.date },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(now = LocalDateTime.now()))

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskRepository.setDone(task.id, !task.done, LocalDate.now()) }
    }

    fun toggleHabit(id: Long) {
        viewModelScope.launch { habitRepository.toggle(id, LocalDate.now()) }
    }

    fun incrementTasbih() = settings.updateTasbih { it.tapped(LocalDate.now()) }

    fun resetTasbih() = settings.updateTasbih { it.copy(count = 0) }

    companion object {
        private const val TICK_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                HomeViewModel(
                    c.taskRepository,
                    c.habitRepository,
                    c.settings,
                    c.counterRepository,
                    c.debtRepository,
                    c.plannerRepository,
                )
            }
        }
    }
}
