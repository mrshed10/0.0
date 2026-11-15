package com.athar.app.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Per-widget configuration, keyed by appWidgetId. */
class WidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("athar_widgets", Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): WidgetConfig? = WidgetJson.decode(prefs.getString(key(appWidgetId), null))

    fun put(appWidgetId: Int, config: WidgetConfig) {
        prefs.edit().putString(key(appWidgetId), WidgetJson.encode(config)).commit()
        changes.value++
    }

    fun remove(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
        changes.value++
    }

    /** Config chosen in the app before asking the launcher to pin a widget. */
    var pending: WidgetConfig?
        get() = WidgetJson.decode(prefs.getString(KEY_PENDING, null))
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_PENDING) else putString(KEY_PENDING, WidgetJson.encode(value)) }.apply()
        }

    /** Gives the pending config to a freshly pinned widget. */
    fun claimPending(appWidgetId: Int): Boolean {
        val config = pending ?: return false
        put(appWidgetId, config)
        pending = null
        return true
    }

    private fun key(id: Int) = "w_$id"

    companion object {
        private const val KEY_PENDING = "pending"

        /** Bumped whenever any widget's config changes, so live widgets reload. */
        val changes = MutableStateFlow(0L)
    }
}
