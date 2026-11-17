package com.athar.app.core.notify

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.athar.app.MainActivity
import com.athar.app.R
import com.athar.app.feature.prayer.AdhanVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What is sounding right now (for the lock-screen stop screen). */
data class AdhanPlayback(val title: String, val text: String, val fajr: Boolean, val announcement: Boolean)

/**
 * Plays the adhan (and spoken prayer announcements) as a foreground service.
 *
 * Stopping works from the lock screen without unlocking:
 * - the notification is public on the lock screen with a "Stop adhan" button;
 * - a full-screen stop screen opens over the lock screen while the adhan plays
 *   (like an alarm clock).
 * The buttons reach the running service directly, so one tap is enough.
 */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var speaker: Speaker? = null
    private var finished = false
    private var current: Request? = null

    private data class Request(
        val title: String,
        val text: String,
        val fajr: Boolean,
        val announcement: Boolean,
        val kind: AlertKind,
        val tone: String?,
        val volume: AdhanVolume,
        val alarmStream: Boolean,
        val speech: String?,
        val voiceFile: String?,
        val notificationId: Int,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopNow(keepNotice = true)
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A new alert replaces whatever is playing.
        job?.cancel()
        releasePlayer()
        finished = false

        val announcement = intent.getBooleanExtra(EXTRA_ANNOUNCE, false)
        val fajr = intent.getBooleanExtra(EXTRA_FAJR, false)
        val kind = AlertKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
            ?: if (fajr) AlertKind.Fajr else AlertKind.Adhan
        val request = Request(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            fajr = fajr,
            announcement = announcement,
            kind = kind,
            tone = intent.getStringExtra(EXTRA_TONE),
            volume = AdhanVolume.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_VOLUME) } ?: AdhanVolume.Alarm,
            alarmStream = intent.getBooleanExtra(EXTRA_IN_SILENT, true),
            speech = intent.getStringExtra(EXTRA_SPEECH)?.takeIf { it.isNotBlank() },
            voiceFile = intent.getStringExtra(EXTRA_VOICE_FILE),
            notificationId = intent.getIntExtra(EXTRA_ID, NOTIFICATION_ID),
        )
        current = request

        val notification = buildNotification(this, request.kind, request.title, request.text, playing = true, fullScreen = !announcement)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        _playback.value = AdhanPlayback(request.title, request.text, fajr, announcement)

        val audio = getSystemService(AudioManager::class.java)
        val phoneQuiet = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
        if (request.volume == AdhanVolume.Silent || inCall || (phoneQuiet && !request.alarmStream)) {
            stopNow(keepNotice = true)
            return START_NOT_STICKY
        }

        job = scope.launch {
            // Safety stop for very long files.
            withTimeoutOrNull(MAX_PLAY_MS) {
                if (announcement) {
                    // Pre-alert / iqama: short tone, then the spoken sentence.
                    if (request.tone != null) play(request.tone, request.volume, request.alarmStream, limitMs = 15_000)
                    announce(request)
                } else {
                    // Adhan: the spoken announcement first, then the adhan itself.
                    announce(request)
                    play(request.tone, request.volume, request.alarmStream, limitMs = MAX_PLAY_MS)
                }
            }
            stopNow(keepNotice = true)
        }
        return START_NOT_STICKY
    }

    /** A recorded announcement if the user chose one, otherwise the spoken sentence. */
    private suspend fun announce(request: Request) {
        val file = request.voiceFile
        if (file != null) {
            play(file, request.volume, request.alarmStream, limitMs = 60_000)
            delay(250)
            return
        }
        request.speech?.let { speak(it, request) }
    }

    private suspend fun speak(text: String, request: Request) {
        val voice = (application as com.athar.app.AtharApplication).container.settings.prayer.value.voice
        val s = speaker ?: Speaker(this, voice.engine).also { speaker = it }
        s.speak(text, request.alarmStream, request.volume.factor, voice.voiceName, voice.rate, voice.pitch)
        delay(250)
    }

    /** Plays a sound file to the end (or until [limitMs]). */
    private suspend fun play(tone: String?, volume: AdhanVolume, alarmStream: Boolean, limitMs: Long) {
        val uri = tone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        withTimeoutOrNull(limitMs) {
            suspendCancellableCoroutine { cont ->
                val mp = runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        setDataSource(this@AdhanService, uri)
                        setVolume(volume.factor, volume.factor)
                        setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                        setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                        setOnPreparedListener { it.start() }
                        prepareAsync()
                    }
                }.getOrNull()
                if (mp == null) {
                    cont.resume(Unit)
                } else {
                    player = mp
                    cont.invokeOnCancellation { releasePlayer() }
                }
            }
        }
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
    }

    /**
     * Stops sound and the service. [keepNotice] leaves a normal, silent
     * notification ("Time for Dhuhr") without the stop button.
     */
    fun stopNow(keepNotice: Boolean) {
        if (finished) return
        finished = true
        job?.cancel()
        releasePlayer()
        speaker?.stop()
        val request = current
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (keepNotice && request != null) {
            Notifier.postNotice(this, request.notificationId, request.kind, request.title, request.text)
        }
        _playback.value = null
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        releasePlayer()
        speaker?.shutdown()
        speaker = null
        scope.cancel()
        _playback.value = null
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4_242
        const val ACTION_STOP = "com.athar.app.STOP_ADHAN"
        private const val MAX_PLAY_MS = 8 * 60_000L
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_FAJR = "fajr"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_IN_SILENT = "inSilent"
        private const val EXTRA_TONE = "tone"
        private const val EXTRA_SPEECH = "speech"
        private const val EXTRA_VOICE_FILE = "voiceFile"
        private const val EXTRA_ANNOUNCE = "announce"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"

        @Volatile
        private var running: AdhanService? = null

        private val _playback = MutableStateFlow<AdhanPlayback?>(null)

        /** Non-null while an adhan or announcement is sounding. */
        val playback: StateFlow<AdhanPlayback?> = _playback.asStateFlow()

        /** Stops whatever is playing (from the lock screen, the stop screen or the app). */
        fun stop(context: Context) {
            val service = running
            if (service != null) {
                service.stopNow(keepNotice = true)
            } else {
                runCatching { context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_STOP)) }
            }
        }

        /** The adhan, optionally preceded by a spoken [speech]. */
        fun start(
            context: Context,
            fajr: Boolean,
            tone: String?,
            volume: AdhanVolume,
            inSilent: Boolean,
            title: String,
            text: String,
            speech: String? = null,
            voiceFile: String? = null,
        ) {
            val intent = Intent(context, AdhanService::class.java)
                .putExtra(EXTRA_VOICE_FILE, voiceFile)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_FAJR, fajr)
                .putExtra(EXTRA_VOLUME, volume.name)
                .putExtra(EXTRA_IN_SILENT, inSilent)
                .putExtra(EXTRA_TONE, tone)
                .putExtra(EXTRA_SPEECH, speech)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Background start refused: still show the notification.
                Notifier.postNotice(context, NOTIFICATION_ID, if (fajr) AlertKind.Fajr else AlertKind.Adhan, title, text)
            }
        }

        /**
         * A spoken alert (before the adhan or at iqama): an optional short
         * [tone], then [speech]. Falls back to a normal notification.
         */
        fun announce(
            context: Context,
            id: Int,
            kind: AlertKind,
            tone: String?,
            volume: AdhanVolume,
            title: String,
            text: String,
            speech: String,
            voiceFile: String? = null,
        ) {
            val intent = Intent(context, AdhanService::class.java)
                .putExtra(EXTRA_VOICE_FILE, voiceFile)
                .putExtra(EXTRA_ANNOUNCE, true)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_VOLUME, volume.name)
                // Spoken alerts follow the phone's silent mode.
                .putExtra(EXTRA_IN_SILENT, false)
                .putExtra(EXTRA_TONE, tone)
                .putExtra(EXTRA_SPEECH, speech)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Notifier.post(context, id, kind, tone, title, text)
            }
        }

        private fun stopIntent(context: Context, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AdhanStopReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /**
         * [playing]: ongoing, with the stop button (also shown on the lock
         * screen). [fullScreen]: opens the stop screen over the lock screen.
         */
        fun buildNotification(
            context: Context,
            kind: AlertKind,
            title: String,
            text: String,
            playing: Boolean,
            fullScreen: Boolean = false,
        ) = NotificationCompat.Builder(context, Channels.ensure(context, if (kind.silentChannel) kind else AlertKind.Voice, null))
            .setSmallIcon(R.drawable.ic_stat_athar)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(!playing)
            .setOngoing(playing)
            .setColor(0xFFE6C27A.toInt())
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
                if (playing) {
                    // Works on the lock screen: no unlock needed for this button.
                    addAction(
                        NotificationCompat.Action.Builder(0, context.getString(R.string.adhan_stop), stopIntent(context, 1))
                            .setAuthenticationRequired(false)
                            .setShowsUserInterface(false)
                            .build(),
                    )
                    setDeleteIntent(stopIntent(context, 2))
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    if (fullScreen) {
                        setFullScreenIntent(
                            PendingIntent.getActivity(
                                context,
                                3,
                                Intent(context, AdhanAlarmActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            ),
                            true,
                        )
                    }
                }
            }
            .build()
    }
}

/** The notification's "Stop adhan" button (works on the lock screen). */
class AdhanStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AdhanService.stop(context)
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
