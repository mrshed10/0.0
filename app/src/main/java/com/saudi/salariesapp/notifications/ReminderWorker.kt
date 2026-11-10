package com.saudi.salariesapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saudi.salariesapp.MainActivity
import com.saudi.salariesapp.R
import com.saudi.salariesapp.data.AppPreferences
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.EventsRepository
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.widget.SalariesWidget
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * عامل دوري (كل 6 ساعات): يحدّث الودجت (لتتغيّر الأيام المتبقية يومياً)
 * ويرسل تنبيه الخدمات المفعّلة قبل الصرف بيوم ويوم الصرف، وتنبيه العدادات الشخصية يوم الموعد.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        SalariesWidget.refreshAll(applicationContext)
        // لا نرسل تنبيهات ليلاً
        if (LocalTime.now().hour >= 8) sendDueNotifications()
        return Result.success()
    }

    private fun sendDueNotifications() {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = AppPreferences(ctx)
        val repo = EventsRepository(prefs)
        val today = LocalDate.now()
        val store = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        val sent = store.getStringSet("keys", emptySet())!!.toMutableSet()

        val due = mutableListOf<EventModel>()
        PaymentSchedule.services.filter { prefs.isNotificationEnabled(it.id) }.forEach { s ->
            val e = PaymentSchedule.toEvent(s, PaymentSchedule.nextPayment(today, prefs.paymentDay(s)), today)
            if (e.remainingDays in 0..1) due += e
        }
        due += repo.customEvents(today).filter { it.remainingDays == 0 }

        ensureChannel(ctx)
        for (e in due) {
            val key = "${e.id}_${e.remainingDays}"
            if (key in sent) continue
            val body = if (e.remainingDays == 0) "موعد ${e.title} اليوم" else "موعد ${e.title} غداً"
            val intent = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_calendar)
                .setContentTitle(e.title)
                .setContentText("$body • ${e.dateHijri}")
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(key.hashCode(), n)
            sent += key
        }
        // نحدّ حجم سجل التنبيهات المرسلة
        store.edit().putStringSet("keys", sent.toList().takeLast(200).toSet()).apply()
    }

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تنبيهات المواعيد", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "payments"
    }
}

object ReminderScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
