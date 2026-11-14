package com.athar.app.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.athar.app.R
import com.athar.app.core.data.AppSettings
import com.athar.app.core.data.PlannerRepository
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.region.Cities
import com.athar.app.feature.prayer.Prayer
import com.athar.app.feature.reminders.ReminderMath
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.salary.SalarySource
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One scheduled notification. */
data class Alarm(
    val code: Int,
    val at: LocalDateTime,
    val kind: AlertKind,
    val tone: String?,
    val title: String,
    val text: String,
)

/**
 * Keeps exactly one pending alarm per item (its next occurrence). Called on
 * app start, on any data/settings change, after each alarm fires and after reboot.
 */
class AlarmScheduler(
    private val context: Context,
    private val settings: AppSettings,
    private val planner: PlannerRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) {
        val alarms = prayerAlarms(now) + salaryAlarms(now) + reminderAlarms(now)
        val codes = alarms.map { it.code }.toSet()
        (settings.scheduledAlarmCodes - codes).forEach { cancel(it) }
        alarms.forEach { schedule(it) }
        settings.scheduledAlarmCodes = codes
    }

    private val locale get() = context.resources.configuration.locales[0]
    private fun clock(t: java.time.LocalTime) = DateTimeFormatter.ofPattern("h:mm a", locale).format(t)

    private fun prayerAlarms(now: LocalDateTime): List<Alarm> {
        val prefs = settings.prayer.value
        val today = prefs.timesFor(now.toLocalDate())
        val tomorrow = prefs.timesFor(now.toLocalDate().plusDays(1))
        val place = Cities.byId(prefs.cityId)?.name(locale) ?: context.getString(R.string.my_location)
        val result = mutableListOf<Alarm>()
        for (prayer in Prayer.entries.filter { it.isObligatory && it in prefs.adhanEnabled }) {
            val name = context.getString(prayer.label)
            fun next(offsetMinutes: Long): LocalDateTime? = listOf(
                now.toLocalDate() to today,
                now.toLocalDate().plusDays(1) to tomorrow,
            ).firstNotNullOfOrNull { (day, times) ->
                times.firstOrNull { it.prayer == prayer }?.let { day.atTime(it.time).minusMinutes(offsetMinutes) }
                    ?.takeIf { it.isAfter(now) }
            }
            next(0)?.let { at ->
                result += Alarm(
                    code = 1000 + prayer.ordinal,
                    at = at,
                    kind = if (prayer == Prayer.Fajr) AlertKind.Fajr else AlertKind.Adhan,
                    tone = if (prayer == Prayer.Fajr) prefs.fajrTone else prefs.adhanTone,
                    title = context.getString(R.string.notif_adhan_title, name),
                    text = "${clock(at.toLocalTime())} · $place",
                )
            }
            if (prefs.preMinutes > 0) {
                next(prefs.preMinutes.toLong())?.let { at ->
                    result += Alarm(
                        code = 1100 + prayer.ordinal,
                        at = at,
                        kind = AlertKind.PrePrayer,
                        tone = null,
                        title = context.getString(R.string.notif_pre_title, name, prefs.preMinutes.toString()),
                        text = "${clock(at.plusMinutes(prefs.preMinutes.toLong()).toLocalTime())} · $place",
                    )
                }
            }
        }
        return result
    }

    private suspend fun salaryAlarms(now: LocalDateTime): List<Alarm> {
        val general = settings.general.value
        return planner.salaries.first().filter { it.remindDaysBefore >= 0 }.mapNotNull { source ->
            nextSalaryReminder(source, now, general.country.weekend)?.let { (payDate, at) ->
                val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), payDate)
                Alarm(
                    code = 200_000 + source.id.toInt(),
                    at = at,
                    kind = AlertKind.Salary,
                    tone = source.tone ?: general.salaryTone,
                    title = context.getString(
                        if (days == 0L) R.string.notif_salary_today else R.string.notif_salary_soon,
                        source.name,
                    ),
                    text = payDate.formatGregorian(locale, "EEEE d MMMM"),
                )
            }
        }
    }

    private fun nextSalaryReminder(
        source: SalarySource,
        now: LocalDateTime,
        weekend: Set<java.time.DayOfWeek>,
    ): Pair<LocalDate, LocalDateTime>? =
        SalarySchedule.upcoming(source, now.toLocalDate(), weekend, count = 3)
            .map { it to it.minusDays(source.remindDaysBefore.toLong()).atTime(SalarySource.REMINDER_TIME) }
            .firstOrNull { it.second.isAfter(now) }

    private suspend fun reminderAlarms(now: LocalDateTime): List<Alarm> {
        val defaultTone = settings.general.value.reminderTone
        return planner.reminders.first().filter { it.enabled }.mapNotNull { r ->
            ReminderMath.next(r, now)?.let { at ->
                Alarm(
                    code = 100_000 + r.id.toInt(),
                    at = at,
                    kind = AlertKind.Reminder,
                    tone = r.tone ?: defaultTone,
                    title = r.title,
                    text = at.toLocalDate().formatGregorian(locale, "EEEE d MMMM") + " · " + clock(at.toLocalTime()),
                )
            }
        }
    }

    private fun pendingIntent(code: Int, alarm: Alarm?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction("com.athar.app.ALARM_$code")
        if (alarm != null) {
            intent.putExtra(AlarmReceiver.EXTRA_CODE, code)
                .putExtra(AlarmReceiver.EXTRA_KIND, alarm.kind.name)
                .putExtra(AlarmReceiver.EXTRA_TONE, alarm.tone)
                .putExtra(AlarmReceiver.EXTRA_TITLE, alarm.title)
                .putExtra(AlarmReceiver.EXTRA_TEXT, alarm.text)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(alarm: Alarm) {
        val millis = alarm.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(alarm.code, alarm)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancel(code: Int) = alarmManager.cancel(pendingIntent(code, null))
}
