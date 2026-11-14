package com.athar.app.feature.counters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.CounterRepository
import com.athar.app.core.data.appContainer
import com.athar.app.core.time.todayFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class CounterFilter { All, CountDown, CountUp }

data class CountersUiState(
    val today: LocalDate = LocalDate.now(),
    val counters: List<Counter> = emptyList(),
    val totalCount: Int = 0,
    val filter: CounterFilter = CounterFilter.All,
    val loaded: Boolean = false,
)

class CountersViewModel(private val repository: CounterRepository) : ViewModel() {

    private val filter = MutableStateFlow(CounterFilter.All)

    val uiState: StateFlow<CountersUiState> =
        combine(repository.counters, todayFlow(), filter) { all, today, f ->
            val visible = when (f) {
                CounterFilter.All -> all
                CounterFilter.CountDown -> all.filter { it.direction == CounterDirection.CountDown }
                CounterFilter.CountUp -> all.filter { it.direction == CounterDirection.CountUp }
            }
            CountersUiState(today, CounterMath.sort(visible, today), all.size, f, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountersUiState())

    fun setFilter(value: CounterFilter) {
        filter.value = value
    }

    fun save(counter: Counter) {
        viewModelScope.launch { repository.save(counter) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CountersViewModel(appContainer().counterRepository) }
        }
    }
}
