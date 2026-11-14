package com.athar.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.athar.app.core.data.TaskRepository
import com.athar.app.core.data.appContainer
import com.athar.app.core.time.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksUiState(
    val today: LocalDate = LocalDate.now(),
    val sections: List<Pair<TaskSection, List<Task>>> = emptyList(),
    val total: Int = 0,
    val doneCount: Int = 0,
    val loaded: Boolean = false,
)

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {

    val uiState: StateFlow<TasksUiState> =
        combine(repository.tasks, todayFlow()) { tasks, today ->
            TasksUiState(
                today = today,
                sections = TaskGrouping.group(tasks, today),
                total = tasks.size,
                doneCount = tasks.count { it.done },
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun quickAdd(title: String, due: LocalDate?) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.save(Task(title = title, due = due)) }
    }

    fun toggle(task: Task) {
        viewModelScope.launch { repository.setDone(task.id, !task.done, LocalDate.now()) }
    }

    fun save(task: Task) {
        viewModelScope.launch { repository.save(task) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.deleteCompleted() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TasksViewModel(appContainer().taskRepository) }
        }
    }
}
