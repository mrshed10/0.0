package com.athar.app.core.notify

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
import com.athar.app.AtharApplication
import com.athar.app.MainActivity
import com.athar.app.R
import com.athar.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows the notification for a fired alarm, then schedules the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = AlertKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
        if (kind != null) {
            Notifier.post(
                context = context,
                id = intent.getIntExtra(EXTRA_CODE, 0),
                kind = kind,
                tone = intent.getStringExtra(EXTRA_TONE),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            )
        }
        rescheduleAsync(context)
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TONE = "tone"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}

/** Re-creates alarms after reboot, time/zone change, app update or exact-alarm permission change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = rescheduleAsync(context)
}

private fun BroadcastReceiver.rescheduleAsync(context: Context) {
    val pending = goAsync()
    val container = (context.applicationContext as AtharApplication).container
    CoroutineScope(Dispatchers.IO).launch {
        try {
            container.alarmScheduler.rescheduleAll()
            WidgetUpdater.refreshAll(context)
        } finally {
            pending.finish()
        }
    }
}

object Notifier {
    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, id: Int, kind: AlertKind, tone: String?, title: String, text: String) {
        if (!canPost(context)) return
        val channel = Channels.ensure(context, kind, tone)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_athar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setCategory(if (kind.alarmUsage) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
