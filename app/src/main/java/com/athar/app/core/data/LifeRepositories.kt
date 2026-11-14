package com.athar.app.core.data

import com.athar.app.core.database.DebtDao
import com.athar.app.core.database.DebtEntryEntity
import com.athar.app.core.database.HabitDao
import com.athar.app.core.database.HabitEntity
import com.athar.app.core.database.HabitLogEntity
import com.athar.app.core.database.PersonEntity
import com.athar.app.core.designsystem.theme.AccentTone
import com.athar.app.feature.debts.DebtEntry
import com.athar.app.feature.debts.EntryType
import com.athar.app.feature.debts.Person
import com.athar.app.feature.habits.Habit
import com.athar.app.feature.habits.HabitIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HabitRepository(private val dao: HabitDao) {
    val habits: Flow<List<Habit>> = dao.observeHabits().map { list -> list.map { it.toDomain() } }

    /** habitId -> days done. */
    val doneDays: Flow<Map<Long, Set<LocalDate>>> = dao.observeLogs().map { logs ->
        logs.groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.epochDay) }).mapValues { it.value.toSet() }
    }

    suspend fun save(habit: Habit): Long = dao.upsert(habit.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    /** Marks [day] done, or undone if it already was. */
    suspend fun toggle(habitId: Long, day: LocalDate) {
        val epochDay = day.toEpochDay()
        if (dao.countLog(habitId, epochDay) > 0) {
            dao.deleteLog(habitId, epochDay)
        } else {
            dao.insertLog(HabitLogEntity(habitId, epochDay))
        }
    }
}

class DebtRepository(private val dao: DebtDao) {
    val people: Flow<List<Person>> = dao.observePeople().map { list -> list.map { it.toDomain() } }
    val entries: Flow<List<DebtEntry>> = dao.observeEntries().map { list -> list.map { it.toDomain() } }

    suspend fun savePerson(person: Person): Long = dao.upsertPerson(person.toEntity())

    suspend fun deletePerson(id: Long) = dao.deletePerson(id)

    suspend fun saveEntry(entry: DebtEntry): Long = dao.upsertEntry(entry.toEntity())

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun stamp(createdAt: Long) = if (createdAt == 0L) System.currentTimeMillis() else createdAt

private fun HabitEntity.toDomain() = Habit(
    id = id,
    title = title,
    icon = enumOr(icon, HabitIcon.Star),
    tone = enumOr(tone, AccentTone.Rose),
    createdAt = createdAt,
)

private fun Habit.toEntity() = HabitEntity(id, title.trim(), icon.name, tone.name, stamp(createdAt))

private fun PersonEntity.toDomain() = Person(id, name, phone, createdAt)

private fun Person.toEntity() = PersonEntity(id, name.trim(), phone.trim(), stamp(createdAt))

private fun DebtEntryEntity.toDomain() = DebtEntry(
    id = id,
    personId = personId,
    type = enumOr(type, EntryType.Gave),
    amountMinor = amountMinor,
    note = note,
    date = LocalDate.ofEpochDay(epochDay),
    createdAt = createdAt,
)

private fun DebtEntry.toEntity() = DebtEntryEntity(
    id = id,
    personId = personId,
    type = type.name,
    amountMinor = amountMinor,
    note = note.trim(),
    epochDay = date.toEpochDay(),
    createdAt = stamp(createdAt),
)
