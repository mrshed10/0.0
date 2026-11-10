package com.saudi.salariesapp.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saudi.salariesapp.MainActivity
import com.saudi.salariesapp.R
import com.saudi.salariesapp.data.AppPrefs
import com.saudi.salariesapp.data.daysLabel
import java.time.LocalDate

/** Shows the reminder notification, then schedules the next occurrence. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val event = AppPrefs(context).allEvents().firstOrNull { it.id == id }

        if (event != null && canNotify(context)) {
            val days = event.remainingDays(LocalDate.now())
            val text = when (days) {
                0L -> "اليوم: ${event.dateHijri}"
                1L -> "غداً: ${event.dateHijri}"
                else -> "متبقي $days ${daysLabel(days)}"
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(event.title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        }
        ReminderScheduler.rescheduleAll(context)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/** Alarms are cleared on reboot; set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
