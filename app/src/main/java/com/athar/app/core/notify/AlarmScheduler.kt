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
import com.athar.app.feature.prayer.PrayerAlertMath
import com.athar.app.feature.reminders.ReminderMath
import com.athar.app.feature.salary.SalarySchedule
import com.athar.app.feature.salary.SalarySource
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What happens when an alarm fires. */
enum class AlarmAction { Notify, Adhan, SilentOn, SilentOff, Refresh, Tick }

/** One scheduled alarm. */
data class Alarm(
    val code: Int,
    val at: LocalDateTime,
    /** null = no notification (silent refresh / ringer change). */
    val kind: AlertKind?,
    val tone: String?,
    val title: String,
    val text: String,
    val action: AlarmAction = if (kind == null) AlarmAction.Refresh else AlarmAction.Notify,
    /** false = fires only while the phone is awake (no battery cost when idle). */
    val wakeup: Boolean = true,
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
        // Silent mode turned off while a window was running: give the phone its sound back.
        if (!settings.prayer.value.silent.enabled) RingerControl.restore(context)
        val alarms = prayerAlarms(now) + salaryAlarms(now) + reminderAlarms(now) + refreshAlarms(now) + listOfNotNull(clockTick(now))
        val codes = alarms.map { it.code }.toSet()
        (settings.scheduledAlarmCodes - codes).forEach { cancel(it) }
        alarms.forEach { schedule(it) }
        settings.scheduledAlarmCodes = codes
    }

    private val locale get() = context.resources.configuration.locales[0]
    private fun clock(t: java.time.LocalTime) = DateTimeFormatter.ofPattern("h:mm a", locale).format(t)

    /**
     * Per prayer: adhan, up to 3 pre-alerts, iqama alert, and silent on/off.
     * Each item gets its next occurrence (today or tomorrow).
     */
    private fun prayerAlarms(now: LocalDateTime): List<Alarm> {
        val prefs = settings.prayer.value
        val days = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))
        val timesByDay = days.associateWith { prefs.timesFor(it) }
        val place = Cities.byId(prefs.cityId)?.name(locale) ?: context.getString(R.string.my_location)
        val result = mutableListOf<Alarm>()

        for (prayer in Prayer.entries.filter { it.isObligatory }) {
            val alert = prefs.alert(prayer)
            val name = context.getString(prayer.label)
            val adhans = days.mapNotNull { day ->
                timesByDay[day]?.firstOrNull { it.prayer == prayer }?.let { day.atTime(it.time) }
            }
            fun nextAt(transform: (LocalDateTime) -> LocalDateTime?): Pair<LocalDateTime, LocalDateTime>? =
                adhans.firstNotNullOfOrNull { adhan -> transform(adhan)?.takeIf { it.isAfter(now) }?.let { adhan to it } }

            if (alert.adhan) {
                nextAt { it }?.let { (adhan, at) ->
                    result += Alarm(
                        code = 1000 + prayer.ordinal,
                        at = at,
                        kind = if (prayer == Prayer.Fajr) AlertKind.Fajr else AlertKind.Adhan,
                        tone = if (prayer == Prayer.Fajr) prefs.fajrTone else prefs.adhanTone,
                        title = context.getString(R.string.notif_adhan_title, name),
                        text = "${clock(adhan.toLocalTime())} · $place",
                        action = AlarmAction.Adhan,
                    )
                }
            }
            alert.preOffsets().forEachIndexed { i, minutes ->
                nextAt { it.minusMinutes(minutes.toLong()) }?.let { (adhan, at) ->
                    result += Alarm(
                        code = 1100 + prayer.ordinal * 10 + i,
                        at = at,
                        kind = AlertKind.PrePrayer,
                        tone = alert.preTone,
                        title = context.getString(R.string.notif_pre_title, name, minutes.toString()),
                        text = "${clock(adhan.toLocalTime())} · $place",
                    )
                }
            }
            if (alert.iqamaEnabled && alert.iqamaAlert) {
                nextAt { it.plusMinutes(alert.iqamaMinutes.toLong()) }?.let { (_, at) ->
                    result += Alarm(
                        code = 1200 + prayer.ordinal,
                        at = at,
                        kind = AlertKind.Iqama,
                        tone = alert.iqamaTone,
                        title = context.getString(R.string.notif_iqama_title, name),
                        text = clock(at.toLocalTime()),
                    )
                }
            }
            // Silent window: start and end are scheduled independently so an
            // ongoing window still gets its "restore" alarm.
            val windows = adhans.mapNotNull { PrayerAlertMath.silentWindow(prayer, it, alert, prefs.silent) }
            windows.firstOrNull { it.start.isAfter(now) }?.let { w ->
                result += Alarm(
                    code = 1300 + prayer.ordinal,
                    at = w.start,
                    kind = AlertKind.Status,
                    tone = null,
                    title = context.getString(
                        R.string.notif_silent_title,
                        if (w.jumuah) context.getString(R.string.jumuah) else name,
                    ),
                    text = context.getString(R.string.notif_silent_until, clock(w.end.toLocalTime())),
                    action = AlarmAction.SilentOn,
                )
            }
            windows.firstOrNull { it.end.isAfter(now) }?.let { w ->
                result += Alarm(1400 + prayer.ordinal, w.end, null, null, "", "", AlarmAction.SilentOff)
            }
        }
        return result
    }

    /** Widgets show "next prayer" and dates: refresh at each prayer time and after midnight. */
    private fun refreshAlarms(now: LocalDateTime): List<Alarm> {
        val prefs = settings.prayer.value
        val nextPrayer = com.athar.app.feature.prayer.PrayerSchedule.next(
            now,
            prefs.timesFor(now.toLocalDate()),
            prefs.timesFor(now.toLocalDate().plusDays(1)),
        )
        return listOf(
            Alarm(9_000, now.toLocalDate().plusDays(1).atTime(0, 0, 30), null, null, "", ""),
            Alarm(9_001, nextPrayer.at.plusSeconds(5), null, null, "", ""),
        )
    }

    /** Next-minute tick, only while a bitmap clock widget is on the home screen. */
    private fun clockTick(now: LocalDateTime): Alarm? {
        val store = com.athar.app.widget.WidgetStore(context)
        val needed = com.athar.app.widget.WidgetUpdater.placedWidgetIds(context)
            .any { store.get(it)?.content?.type?.minuteClock == true }
        if (!needed) return null
        val next = now.withSecond(0).withNano(0).plusMinutes(1).plusSeconds(1)
        return Alarm(CLOCK_TICK, next, null, null, "", "", AlarmAction.Tick, wakeup = false)
    }

    /** Called on every tick: schedule only the next one (cheap). */
    fun scheduleClockTick(now: LocalDateTime = LocalDateTime.now()) {
        clockTick(now)?.let { schedule(it) } ?: cancel(CLOCK_TICK)
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
                .putExtra(AlarmReceiver.EXTRA_KIND, alarm.kind?.name)
                .putExtra(AlarmReceiver.EXTRA_ACTION, alarm.action.name)
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
        when {
            !alarm.wakeup && canScheduleExact() -> alarmManager.setExact(AlarmManager.RTC, millis, pi)
            !alarm.wakeup -> alarmManager.set(AlarmManager.RTC, millis, pi)
            canScheduleExact() -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancel(code: Int) = alarmManager.cancel(pendingIntent(code, null))

    private companion object {
        const val CLOCK_TICK = 9_002
    }
}
