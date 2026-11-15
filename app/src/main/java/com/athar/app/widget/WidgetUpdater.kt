package com.athar.app.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.athar.app.AtharApplication
import com.athar.app.MainActivity
import com.athar.app.R
import com.athar.app.core.notify.Notifier
import com.athar.app.feature.prayer.PrayerSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

object WidgetUpdater {
    private val _version = MutableStateFlow(0L)

    /** Bumped on every refresh; live widgets collect it and reload their data. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _tick = MutableStateFlow(0L)

    /** Bumped each minute for bitmap clocks only. */
    val tick: StateFlow<Long> = _tick.asStateFlow()

    /** Redraws only the minute-clock widgets. */
    suspend fun refreshClocks(context: Context) {
        _tick.update { it + 1 }
        val store = WidgetStore(context)
        val manager = GlanceAppWidgetManager(context)
        placedWidgetIds(context)
            .filter { store.get(it)?.needsMinuteTick() == true }
            .forEach { id -> runCatching { AtharWidget().update(context, manager.getGlanceIdBy(id)) } }
    }

    /** Reloads every placed widget (live or not) and the lock-screen card. */
    suspend fun refreshAll(context: Context) {
        _version.update { it + 1 }
        runCatching { AtharWidget().updateAll(context) }
        runCatching { LockCard.update(context) }
    }

    suspend fun refresh(context: Context, appWidgetId: Int) {
        _version.update { it + 1 }
        runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            AtharWidget().update(context, glanceId)
        }
    }

    fun placedWidgetIds(context: Context): List<Int> {
        val awm = AppWidgetManager.getInstance(context)
        return WidgetProviders.receivers
            .flatMap { awm.getAppWidgetIds(ComponentName(context, it)).toList() }
    }
}

/** Asks the launcher to place a widget showing [content] (Android 8+, supported by Samsung One UI). */
object WidgetPinner {
    fun pin(context: Context, content: WidgetContent): Boolean = pin(context, WidgetConfig(content))

    /** Places a widget with a fully customized [config] (from the gallery); [small] picks the 2×2 size. */
    fun pin(context: Context, config: WidgetConfig, small: Boolean? = null): Boolean {
        val content = config.content
        val awm = AppWidgetManager.getInstance(context)
        if (!awm.isRequestPinAppWidgetSupported) return false
        WidgetStore(context).pending = config
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val callback = PendingIntent.getBroadcast(context, 1, Intent(context, WidgetPinnedReceiver::class.java), flags)
        val provider = if (small ?: content.type.prefersSmall) AtharSmallWidgetReceiver::class.java else AtharWidgetReceiver::class.java
        return awm.requestPinAppWidget(ComponentName(context, provider), null, callback)
    }
}

private val WidgetType.prefersSmall: Boolean
    get() = this in setOf(
        WidgetType.Counter, WidgetType.Salary, WidgetType.Habit, WidgetType.Tasbih,
        WidgetType.DebtPerson, WidgetType.Date, WidgetType.AnalogClock,
    )

/** Called by the launcher once a pinned widget exists: give it the chosen content. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (!WidgetStore(context).claimPending(id)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refresh(context, id)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Samsung phones do not accept third-party lock-screen widgets, so the
 * lock-screen card is a silent ongoing notification (visible on the lock
 * screen) that mirrors a chosen item, with a live countdown for prayers.
 */
object LockCard {
    private const val NOTIFICATION_ID = 7_770
    private const val CHANNEL = "lock_card"

    suspend fun update(context: Context) {
        val container = (context.applicationContext as AtharApplication).container
        val choice = container.settings.lockCard.value
        val nm = NotificationManagerCompat.from(context)
        if (choice == null || !Notifier.canPost(context)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(context)
        val data = WidgetDataLoader.load(context, WidgetConfig(choice))
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_athar)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        when (data) {
            is WidgetData.NextPrayer -> {
                val prefs = container.settings.prayer.value
                val now = LocalDateTime.now()
                val next = PrayerSchedule.next(now, prefs.timesFor(now.toLocalDate()), prefs.timesFor(now.toLocalDate().plusDays(1)))
                builder.setContentTitle("🕌 ${data.name} · ${data.time}")
                    .setContentText(data.place)
                    .setWhen(next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            is WidgetData.Big -> builder.setContentTitle("${data.title}: ${data.value} ${data.unit}".trim())
                .setContentText(listOf(data.line1, data.line2).filter { it.isNotBlank() }.joinToString(" · "))
            is WidgetData.Tasks -> builder.setContentTitle("✅ " + context.getString(R.string.today_tasks) + " · " + data.summary)
                .setContentText(data.rows.filterNot { it.done }.take(3).joinToString(" • ") { it.title })
            is WidgetData.DateInfo -> builder.setContentTitle(data.day).setContentText("${data.gregorian} · ${data.hijri}")
            is WidgetData.Verse -> builder.setContentTitle(data.text).setContentText(data.ref)
            is WidgetData.Tasbih -> builder.setContentTitle("📿 ${data.dhikr}").setContentText("${data.count} / ${data.target} · ${data.today}")
            is WidgetData.Debts -> builder.setContentTitle("💼 ${data.owedLabel} ${data.owed} · ${data.oweLabel} ${data.owe}")
            is WidgetData.Habits -> builder.setContentTitle("🔥 " + context.getString(R.string.habits))
                .setContentText(data.rows.joinToString(" • ") { "${if (it.doneToday) "✅" else it.emoji} ${it.title} ${it.streak}" })
            is WidgetData.Schedule -> builder.setContentTitle("🗓️ " + context.getString(R.string.widget_type_schedule))
                .setContentText(data.rows.take(3).joinToString(" • ") { "${it.name}: ${it.daysText}" })
            is WidgetData.Clock -> builder.setContentTitle(data.day).setContentText(data.hijri)
            is WidgetData.Dots -> builder.setContentTitle("${data.title} · ${data.percent}%")
            is WidgetData.NothingStyle -> builder.setContentTitle(listOfNotNull(data.spec.top, data.spec.big).joinToString(" · "))
                .setContentText(data.spec.bottom.orEmpty())
            is WidgetData.Custom -> builder.setContentTitle(data.lines.firstOrNull()?.text.orEmpty())
                .setContentText(data.lines.drop(1).joinToString(" · ") { it.text })
            is WidgetData.Calligraphy -> builder.setContentTitle(data.main).setContentText(data.sub.orEmpty())
            is WidgetData.WorldClock, is WidgetData.WideClock, is WidgetData.BigClock, is WidgetData.MonthGrid -> {
                val today = java.time.LocalDate.now()
                builder.setContentTitle(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, context.resources.configuration.locales[0]))
                    .setContentText(today.toString())
            }
            WidgetData.Missing, WidgetData.Unconfigured -> {
                nm.cancel(NOTIFICATION_ID)
                return
            }
        }
        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission revoked.
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.lock_card_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }
}
