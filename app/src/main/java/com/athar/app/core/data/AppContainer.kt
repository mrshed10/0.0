package com.athar.app.core.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.athar.app.AtharApplication
import com.athar.app.core.database.AtharDatabase

/** Manual dependency container; can be swapped for Hilt when the app grows. */
class AppContainer(context: Context) {
    private val database = AtharDatabase.build(context)

    val counterRepository = CounterRepository(database.counterDao())
    val taskRepository = TaskRepository(database.taskDao())
}

/** For use inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AtharApplication).container
