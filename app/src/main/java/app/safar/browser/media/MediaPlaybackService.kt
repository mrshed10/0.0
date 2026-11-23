package app.safar.browser.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.safar.browser.MainActivity
import app.safar.browser.R
import org.mozilla.geckoview.MediaSession

/** يحمل جلسة الوسائط النشطة في Gecko ليتحكم بها الإشعار. */
object MediaBridge {
    @Volatile var session: MediaSession? = null
    @Volatile var title: String = ""
    @Volatile var artist: String = ""
    @Volatile var playing: Boolean = false
}

/**
 * خدمة أمامية لتشغيل الصوت في الخلفية عند تبديل التطبيقات أو قفل الشاشة.
 * تبقي عملية التطبيق حية + إشعار وسائط بأزرار تشغيل/إيقاف.
 */
class MediaPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> MediaBridge.session?.play()
            ACTION_PAUSE -> MediaBridge.session?.pause()
            ACTION_STOP -> { MediaBridge.session?.stop(); stopSelf(); return START_NOT_STICKY }
        }
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        return START_NOT_STICKY
    }

    private fun action(a: String, req: Int) = PendingIntent.getService(
        this, req, Intent(this, MediaPlaybackService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(MediaBridge.title.ifBlank { getString(R.string.app_name) })
        .setContentText(MediaBridge.artist)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .setOngoing(MediaBridge.playing)
        .setSilent(true)
        .addAction(
            if (MediaBridge.playing) NotificationCompat.Action(android.R.drawable.ic_media_pause, "إيقاف مؤقت", action(ACTION_PAUSE, 1))
            else NotificationCompat.Action(android.R.drawable.ic_media_play, "تشغيل", action(ACTION_PLAY, 2))
        )
        .addAction(NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", action(ACTION_STOP, 3)))
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.media_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val CHANNEL = "media"
        private const val NOTIF_ID = 42
        const val ACTION_UPDATE = "update"
        const val ACTION_PLAY = "play"
        const val ACTION_PAUSE = "pause"
        const val ACTION_STOP = "stop"

        fun update(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, MediaPlaybackService::class.java).setAction(ACTION_UPDATE)
            )
        }

        fun stop(context: Context) = context.stopService(Intent(context, MediaPlaybackService::class.java))
    }
}
