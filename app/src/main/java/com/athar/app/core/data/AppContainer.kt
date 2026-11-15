package com.athar.app.core.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.athar.app.AtharApplication
import com.athar.app.core.backup.BackupManager
import com.athar.app.core.database.AtharDatabase
import com.athar.app.core.notify.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import com.athar.app.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Manual dependency container; can be swapped for Hilt when the app grows. */
class AppContainer(context: Context) {
    private val database = AtharDatabase.build(context)

    val settings = AppSettings(context)
    val counterRepository = CounterRepository(database.counterDao())
    val taskRepository = TaskRepository(database.taskDao())
    val habitRepository = HabitRepository(database.habitDao())
    val debtRepository = DebtRepository(database.debtDao())
    val plannerRepository = PlannerRepository(database.plannerDao())
    val backupManager = BackupManager(database, settings)
    val debtsSession = DebtsSession()
    val alarmScheduler = AlarmScheduler(context, settings, plannerRepository)

    /** Re-plans all notifications whenever anything they depend on changes. */
    fun keepAlarmsInSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                settings.prayer,
                settings.general,
                plannerRepository.salaries,
                plannerRepository.reminders,
                com.athar.app.widget.WidgetStore.changes, // minute tick for new clock widgets
            ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
                .collectLatest {
                    delay(300) // coalesce bursts of edits
                    runCatching { alarmScheduler.rescheduleAll() }
                }
        }
    }
}

/** Redraws widgets and the lock-screen card when any shown data changes. */
fun AppContainer.keepWidgetsInSync(context: Context, scope: CoroutineScope) {
    scope.launch {
        merge(
            counterRepository.counters,
            taskRepository.tasks,
            habitRepository.habits,
            habitRepository.doneDays,
            debtRepository.people,
            debtRepository.entries,
            plannerRepository.salaries,
            plannerRepository.reminders,
            settings.prayer,
            settings.general,
            settings.tasbih,
            settings.lockCard,
        ).collectLatest {
            delay(600)
            WidgetUpdater.refreshAll(context)
        }
    }
}

/** For use inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer = atharApplication().container

fun CreationExtras.atharApplication(): AtharApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AtharApplication
