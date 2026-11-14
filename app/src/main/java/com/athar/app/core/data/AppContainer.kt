package com.athar.app.core.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.athar.app.AtharApplication
import com.athar.app.core.backup.BackupManager
import com.athar.app.core.database.AtharDatabase

/** Manual dependency container; can be swapped for Hilt when the app grows. */
class AppContainer(context: Context) {
    private val database = AtharDatabase.build(context)

    val counterRepository = CounterRepository(database.counterDao())
    val taskRepository = TaskRepository(database.taskDao())
    val habitRepository = HabitRepository(database.habitDao())
    val debtRepository = DebtRepository(database.debtDao())
    val backupManager = BackupManager(database)
    val settings = AppSettings(context)
    val debtsSession = DebtsSession()
}

/** For use inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer = atharApplication().container

fun CreationExtras.atharApplication(): AtharApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AtharApplication
