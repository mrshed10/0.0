package com.athar.app.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small key-value preferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("athar_settings", Context.MODE_PRIVATE)

    private val _debtsLock = MutableStateFlow(prefs.getBoolean(KEY_DEBTS_LOCK, false))
    val debtsLock: StateFlow<Boolean> = _debtsLock.asStateFlow()

    fun setDebtsLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBTS_LOCK, enabled).apply()
        _debtsLock.value = enabled
    }

    private companion object {
        const val KEY_DEBTS_LOCK = "debts_lock"
    }
}

/** In-memory unlock state for the debts section; re-locks when the app goes to background. */
class DebtsSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}
