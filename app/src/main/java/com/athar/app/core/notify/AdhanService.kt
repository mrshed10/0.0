package com.athar.app.core.notify

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.athar.app.MainActivity
import com.athar.app.R
import com.athar.app.feature.prayer.AdhanVolume

/**
 * Plays the adhan once at the chosen volume, as a foreground service with a
 * "Stop" button. On the alarm stream it is heard even in silent/vibrate mode.
 */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val fajr = intent?.getBooleanExtra(EXTRA_FAJR, false) ?: false
        val volume = AdhanVolume.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_VOLUME) } ?: AdhanVolume.Alarm
        val inSilent = intent?.getBooleanExtra(EXTRA_IN_SILENT, true) ?: true
        val tone = intent?.getStringExtra(EXTRA_TONE)

        val notification = buildNotification(this, if (fajr) AlertKind.Fajr else AlertKind.Adhan, title, text, withStop = true)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

        val audio = getSystemService(AudioManager::class.java)
        val phoneQuiet = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        if (volume == AdhanVolume.Silent || (phoneQuiet && !inSilent)) {
            finishKeepingNotification(title, text, fajr)
            return START_NOT_STICKY
        }
        play(tone, volume, inSilent, onDone = { finishKeepingNotification(title, text, fajr) })
        // Safety stop for very long files.
        handler.postDelayed({ finishKeepingNotification(title, text, fajr) }, 8 * 60_000L)
        return START_NOT_STICKY
    }

    private fun play(tone: String?, volume: AdhanVolume, alarmStream: Boolean, onDone: () -> Unit) {
        val uri = tone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(this@AdhanService, uri)
                setVolume(volume.factor, volume.factor)
                setOnCompletionListener { onDone() }
                setOnErrorListener { _, _, _ -> onDone(); true }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrElse {
            onDone()
            null
        }
    }

    /** Stops sound and the service but leaves a normal (dismissible) adhan notification. */
    private fun finishKeepingNotification(title: String, text: String, fajr: Boolean) {
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifier.postSilentAdhan(this, fajr, title, text)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop() }
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4_242
        const val ACTION_STOP = "com.athar.app.STOP_ADHAN"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_FAJR = "fajr"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_IN_SILENT = "inSilent"
        private const val EXTRA_TONE = "tone"

        fun start(context: Context, fajr: Boolean, tone: String?, volume: AdhanVolume, inSilent: Boolean, title: String, text: String) {
            val intent = Intent(context, AdhanService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_FAJR, fajr)
                .putExtra(EXTRA_VOLUME, volume.name)
                .putExtra(EXTRA_IN_SILENT, inSilent)
                .putExtra(EXTRA_TONE, tone)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Background start refused: still show the notification.
                Notifier.postSilentAdhan(context, fajr, title, text)
            }
        }

        fun buildNotification(context: Context, kind: AlertKind, title: String, text: String, withStop: Boolean) =
            NotificationCompat.Builder(context, Channels.ensure(context, kind, null))
                .setSmallIcon(R.drawable.ic_stat_athar)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_TAB, "prayer")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .apply {
                    if (withStop) {
                        addAction(
                            0,
                            context.getString(R.string.adhan_stop),
                            PendingIntent.getService(
                                context,
                                1,
                                Intent(context, AdhanService::class.java).setAction(ACTION_STOP),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        setDeleteIntent(
                            PendingIntent.getService(
                                context,
                                2,
                                Intent(context, AdhanService::class.java).setAction(ACTION_STOP),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                    }
                }
                .build()
    }
}

/** Puts the phone on silent/vibrate for a prayer and restores it afterwards. */
object RingerControl {
    private const val PREFS = "athar_ringer"
    private const val KEY_SAVED = "saved_mode"

    fun hasPolicyAccess(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** Returns false if the system refused (e.g. no Do Not Disturb access for silent mode). */
    fun enable(context: Context, mode: com.athar.app.feature.prayer.SilentMode): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = if (mode == com.athar.app.feature.prayer.SilentMode.Silent && hasPolicyAccess(context)) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }
        if (audio.ringerMode == target) return true
        if (!prefs.contains(KEY_SAVED)) prefs.edit().putInt(KEY_SAVED, audio.ringerMode).apply()
        return try {
            audio.ringerMode = target
            true
        } catch (e: SecurityException) {
            prefs.edit().remove(KEY_SAVED).apply()
            false
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_SAVED, -1)
        if (saved < 0) return
        try {
            context.getSystemService(AudioManager::class.java).ringerMode = saved
        } catch (e: SecurityException) {
            // Access was revoked; leave the phone as it is.
        }
        prefs.edit().remove(KEY_SAVED).apply()
    }
}
