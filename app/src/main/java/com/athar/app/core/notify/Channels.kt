package com.athar.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.StringRes
import com.athar.app.R

enum class AlertKind(val key: String, @StringRes val label: Int, val alarmUsage: Boolean) {
    Fajr("fajr", R.string.channel_fajr, true),
    Adhan("adhan", R.string.channel_adhan, true),
    PrePrayer("pre", R.string.channel_pre_prayer, false),
    Salary("salary", R.string.channel_salary, false),
    Reminder("reminder", R.string.channel_reminder, false),
}

/**
 * A channel's sound cannot change after creation, so each (kind, tone) pair
 * gets its own channel id; stale ones are removed.
 */
object Channels {
    fun ensure(context: Context, kind: AlertKind, tone: String?): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        val hash = (tone ?: "default").hashCode().toUInt().toString(16)
        val id = "${kind.key}_$hash"
        val sound: Uri = tone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Tones.isImported(context, sound)) {
            // Let the system UI read our private file (grants do not survive reboots).
            context.grantUriPermission("com.android.systemui", sound, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (nm.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id,
                context.getString(kind.label) + " · " + Tones.title(context, tone),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(if (kind.alarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }
        if (kind != AlertKind.Reminder) {
            nm.notificationChannels
                .filter { it.id.startsWith(kind.key + "_") && it.id != id }
                .forEach { nm.deleteNotificationChannel(it.id) }
        }
        return id
    }
}
