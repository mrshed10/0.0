package com.livetranslate.overlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.livetranslate.overlay.MainActivity
import com.livetranslate.overlay.R

/**
 * إشعار الخدمة الأمامية (شرط النظام لبقاء الترجمة تعمل في الخلفية).
 * هو المكان الوحيد للتحكم والحالة، بدلاً من أي عنصر عائم على الشاشة.
 */
object NotificationHelper {
    const val NOTIFICATION_ID = 42
    private const val CHANNEL_ID = "overlay"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, text: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags,
        )
        val stop = PendingIntent.getService(
            context, 1, OverlayService.intent(context, OverlayService.ACTION_STOP), flags,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(context.getString(R.string.notif_title_active))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_action_stop), stop)
            .build()
    }
}
