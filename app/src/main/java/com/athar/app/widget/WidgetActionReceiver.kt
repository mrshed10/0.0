package com.athar.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.athar.app.AtharApplication
import com.athar.app.core.data.tapped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles taps on widget buttons in the background (tick a task, mark a
 * habit, count tasbih) without opening the app, then redraws the widgets.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val pending = goAsync()
        val container = (context.applicationContext as AtharApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                when (action) {
                    TOGGLE_TASK -> {
                        val task = container.taskRepository.tasks.first().firstOrNull { it.id == id }
                        if (task != null) container.taskRepository.setDone(task.id, !task.done, today)
                    }
                    TOGGLE_HABIT -> if (id >= 0) container.habitRepository.toggle(id, today)
                    TASBIH -> container.settings.updateTasbih { it.tapped(today) }
                    REFRESH -> Unit
                }
                WidgetUpdater.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TOGGLE_TASK = "com.athar.app.widget.TOGGLE_TASK"
        const val TOGGLE_HABIT = "com.athar.app.widget.TOGGLE_HABIT"
        const val TASBIH = "com.athar.app.widget.TASBIH"
        const val REFRESH = "com.athar.app.widget.REFRESH"
        private const val EXTRA_ID = "id"

        /** A unique data URI per item keeps each button's PendingIntent separate. */
        fun intent(context: Context, action: String, id: Long): Intent =
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("athar://widget/${action.substringAfterLast('.')}/$id"))
                .putExtra(EXTRA_ID, id)
    }
}
