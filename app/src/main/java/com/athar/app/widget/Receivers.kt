package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * One receiver per entry in the launcher's widget picker. They all draw the
 * same [AtharWidget]; typed entries start with a sensible content and design
 * so the widget works even if the launcher skips the setup screen.
 */
abstract class AtharReceiverBase : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AtharWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::remove)
    }
}

/** Fully custom (wide). */
class AtharWidgetReceiver : AtharReceiverBase()

/** Fully custom (square). */
class AtharSmallWidgetReceiver : AtharReceiverBase()
class PrayerWidgetReceiver : AtharReceiverBase()
class ClockWidgetReceiver : AtharReceiverBase()
class CalendarWidgetReceiver : AtharReceiverBase()
class ScheduleWidgetReceiver : AtharReceiverBase()
class TasksWidgetReceiver : AtharReceiverBase()
class HabitsWidgetReceiver : AtharReceiverBase()
class CalligraphyWidgetReceiver : AtharReceiverBase()
class NothingWidgetReceiver : AtharReceiverBase()

object WidgetProviders {
    /** Receiver → starting design (template id); null = let the user choose. */
    private val defaults: Map<Class<out AtharReceiverBase>, String?> = mapOf(
        AtharWidgetReceiver::class.java to null,
        AtharSmallWidgetReceiver::class.java to null,
        PrayerWidgetReceiver::class.java to "prayer_table",
        ClockWidgetReceiver::class.java to "wide_clock",
        CalendarWidgetReceiver::class.java to "date_simple",
        ScheduleWidgetReceiver::class.java to "schedule",
        TasksWidgetReceiver::class.java to "tasks",
        HabitsWidgetReceiver::class.java to "habits_row",
        CalligraphyWidgetReceiver::class.java to "cal_day_ar",
        NothingWidgetReceiver::class.java to "nothing_clock",
    )

    val receivers: List<Class<out AtharReceiverBase>> get() = defaults.keys.toList()

    /** The starting design for a placed widget's picker entry, if it has one. */
    fun defaultConfig(context: Context, appWidgetId: Int): WidgetConfig? {
        val className = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ?: return null
        val templateId = defaults.entries.firstOrNull { it.key.name == className }?.value ?: return null
        return WidgetTemplates.all.firstOrNull { it.id == templateId }?.config
    }
}
