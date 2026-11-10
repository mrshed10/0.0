package com.saudi.salariesapp.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.saudi.salariesapp.data.AppPrefs
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.PaymentSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Per-event reminders: a notification at 9:00 the day before each enabled event
 * (or at 9:00 on the day itself if that moment has already passed).
 * Uses inexact alarms, so no exact-alarm permission is needed.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "payment_reminders"
    const val EXTRA_EVENT_ID = "event_id"
    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "تنبيهات المواعيد والرواتب", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "تنبيه قبل موعد صرف الرواتب والمواعيد المختارة"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun rescheduleAll(context: Context) {
        val prefs = AppPrefs(context)
        val enabled = prefs.loadReminderIds()
        val events = prefs.allEvents()
        val alarm = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()

        // Cancel everything we may have set, then set the enabled ones again.
        events.forEach { alarm.cancel(pendingIntent(context, it.id)) }

        events.filter { it.id in enabled }.forEach { event ->
            val trigger = nextReminder(event, now) ?: return@forEach
            val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context, event.id))
        }
    }

    /**
     * Next reminder moment for [event]. Repeating events (payments, occasions) roll over to
     * their following occurrence once today's reminder has passed, so reminders keep coming.
     */
    fun nextReminder(event: EventModel, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val candidates: List<LocalDate> = when {
            event.isCustom -> listOf(event.date)
            else -> {
                val rule = PaymentSchedule.monthlyRules.firstOrNull { it.id == event.id }
                val occasion = PaymentSchedule.occasionRules.firstOrNull { it.id == event.id }
                when {
                    rule != null -> PaymentSchedule.upcomingPayments(rule, today, 3)
                    occasion != null -> {
                        val first = PaymentSchedule.nextOccurrence(occasion, today)
                        listOf(first, PaymentSchedule.nextOccurrence(occasion, first.plusDays(1)))
                    }
                    else -> listOf(event.date)
                }
            }
        }
        return candidates.firstNotNullOfOrNull { reminderTime(it, now) }
    }

    /** Day before at 9:00; falls back to the day itself; null if the event is past. */
    fun reminderTime(eventDate: LocalDate, now: LocalDateTime): LocalDateTime? {
        val dayBefore = eventDate.minusDays(1).atTime(REMINDER_TIME)
        val sameDay = eventDate.atTime(REMINDER_TIME)
        return when {
            dayBefore.isAfter(now) -> dayBefore
            sameDay.isAfter(now) -> sameDay
            else -> null
        }
    }

    private fun pendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.saudi.salariesapp.REMINDER.$eventId")
            .putExtra(EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(
            context, eventId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
