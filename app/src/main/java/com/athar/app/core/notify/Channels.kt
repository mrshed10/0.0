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

/**
 * [silentChannel]: the channel itself makes no sound (the adhan is played by
 * [AdhanService] at the chosen volume). [multiTone]: several tones may coexist
 * (e.g. a different pre-alert tone per prayer).
 */
enum class AlertKind(
    val key: String,
    @StringRes val label: Int,
    val alarmUsage: Boolean,
    val silentChannel: Boolean = false,
    val multiTone: Boolean = false,
    val importance: Int = NotificationManager.IMPORTANCE_HIGH,
) {
    Fajr("fajr2", R.string.channel_fajr, true, silentChannel = true),
    Adhan("adhan2", R.string.channel_adhan, true, silentChannel = true),
    PrePrayer("pre", R.string.channel_pre_prayer, false, multiTone = true),
    Iqama("iqama", R.string.channel_iqama, false, multiTone = true),
    Salary("salary", R.string.channel_salary, false),
    Reminder("reminder", R.string.channel_reminder, false, multiTone = true),
    Status("status", R.string.channel_status, false, silentChannel = true, importance = NotificationManager.IMPORTANCE_LOW),
}

/**
 * A channel's sound cannot change after creation, so each (kind, tone) pair
 * gets its own channel id; stale ones are removed.
 */
object Channels {
    /** Channels from older versions that are replaced. */
    private val legacyPrefixes = listOf("fajr_", "adhan_")

    fun ensure(context: Context, kind: AlertKind, tone: String?): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = if (kind.silentChannel) kind.key else "${kind.key}_${(tone ?: "default").hashCode().toUInt().toString(16)}"

        val sound: Uri? = if (kind.silentChannel) null else tone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (sound != null && Tones.isImported(context, sound)) {
            // Let the system UI read our private file (grants do not survive reboots).
            context.grantUriPermission("com.android.systemui", sound, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (nm.getNotificationChannel(id) == null) {
            val name = if (kind.silentChannel) context.getString(kind.label) else context.getString(kind.label) + " · " + Tones.title(context, tone)
            val channel = NotificationChannel(id, name, kind.importance)
            if (sound == null) {
                channel.setSound(null, null)
            } else {
                channel.setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(if (kind.alarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            channel.enableVibration(kind.importance >= NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val stale = nm.notificationChannels.filter { ch ->
            ch.id != id && (
                (!kind.multiTone && !kind.silentChannel && ch.id.startsWith(kind.key + "_")) ||
                    legacyPrefixes.any { ch.id.startsWith(it) }
                )
        }
        stale.forEach { nm.deleteNotificationChannel(it.id) }
        return id
    }
}
