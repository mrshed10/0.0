package com.athar.app.core.data

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.database.PlannerDao
import com.athar.app.core.database.ReminderEntity
import com.athar.app.core.database.SalarySourceEntity
import com.athar.app.feature.reminders.Reminder
import com.athar.app.feature.reminders.Repeat
import com.athar.app.feature.salary.SalarySource
import com.athar.app.feature.salary.WeekendShift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

class PlannerRepository(private val dao: PlannerDao) {
    val salaries: Flow<List<SalarySource>> = dao.observeSalaries().map { list -> list.map { it.toDomain() } }
    val reminders: Flow<List<Reminder>> = dao.observeReminders().map { list -> list.map { it.toDomain() } }

    suspend fun saveSalary(source: SalarySource): Long = dao.upsertSalary(source.toEntity())
    suspend fun deleteSalary(id: Long) = dao.deleteSalary(id)

    /** Adds [presets] once, only if the user has no salary entries yet. */
    suspend fun seedIfEmpty(presets: List<SalarySource>): Boolean {
        if (dao.salaryCount() > 0) return false
        presets.forEachIndexed { i, p -> dao.upsertSalary(p.toEntity().copy(createdAt = System.currentTimeMillis() + i)) }
        return true
    }

    suspend fun saveReminder(reminder: Reminder): Long = dao.upsertReminder(reminder.toEntity())
    suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)
    suspend fun setReminderEnabled(id: Long, enabled: Boolean) = dao.setReminderEnabled(id, enabled)
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun stamp(createdAt: Long) = if (createdAt == 0L) System.currentTimeMillis() else createdAt

private fun SalarySourceEntity.toDomain() = SalarySource(
    id = id,
    name = name,
    payDay = payDay,
    calendar = enumOr(calendar, CalendarKind.Gregorian),
    shift = enumOr(shift, WeekendShift.Before),
    remindDaysBefore = remindDaysBefore,
    tone = tone,
    createdAt = createdAt,
)

private fun SalarySource.toEntity() = SalarySourceEntity(
    id = id,
    name = name.trim(),
    payDay = payDay,
    calendar = calendar.name,
    shift = shift.name,
    remindDaysBefore = remindDaysBefore,
    tone = tone,
    createdAt = stamp(createdAt),
)

private fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    date = LocalDate.ofEpochDay(epochDay),
    time = LocalTime.ofSecondOfDay(minuteOfDay.coerceIn(0, 1439) * 60L),
    repeat = enumOr(repeatRule, Repeat.Once),
    calendar = enumOr(calendar, CalendarKind.Gregorian),
    tone = tone,
    enabled = enabled,
    createdAt = createdAt,
)

private fun Reminder.toEntity() = ReminderEntity(
    id = id,
    title = title.trim(),
    epochDay = date.toEpochDay(),
    minuteOfDay = time.hour * 60 + time.minute,
    repeatRule = repeat.name,
    calendar = calendar.name,
    tone = tone,
    enabled = enabled,
    createdAt = stamp(createdAt),
)
