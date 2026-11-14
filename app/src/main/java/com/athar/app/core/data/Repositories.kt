package com.athar.app.core.data

import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.database.CounterDao
import com.athar.app.core.database.CounterEntity
import com.athar.app.core.database.TaskDao
import com.athar.app.core.database.TaskEntity
import com.athar.app.core.designsystem.theme.AccentTone
import com.athar.app.feature.counters.Counter
import com.athar.app.feature.counters.CounterDirection
import com.athar.app.feature.tasks.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CounterRepository(private val dao: CounterDao) {
    val counters: Flow<List<Counter>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(counter: Counter): Long = dao.upsert(counter.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)
}

class TaskRepository(private val dao: TaskDao) {
    val tasks: Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(task: Task): Long = dao.upsert(task.toEntity())

    suspend fun setDone(id: Long, done: Boolean, today: LocalDate) =
        dao.setDone(id, done, if (done) today.toEpochDay() else null)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteCompleted() = dao.deleteCompleted()
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun now() = System.currentTimeMillis()

private fun CounterEntity.toDomain() = Counter(
    id = id,
    title = title,
    date = LocalDate.ofEpochDay(dateEpochDay),
    direction = enumOr(direction, CounterDirection.CountDown),
    tone = enumOr(tone, AccentTone.Gold),
    calendar = enumOr(calendar, CalendarKind.Gregorian),
    createdAt = createdAt,
)

private fun Counter.toEntity() = CounterEntity(
    id = id,
    title = title.trim(),
    dateEpochDay = date.toEpochDay(),
    direction = direction.name,
    tone = tone.name,
    calendar = calendar.name,
    createdAt = if (createdAt == 0L) now() else createdAt,
)

private fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    due = dueEpochDay?.let(LocalDate::ofEpochDay),
    done = done,
    completedOn = completedEpochDay?.let(LocalDate::ofEpochDay),
    createdAt = createdAt,
)

private fun Task.toEntity() = TaskEntity(
    id = id,
    title = title.trim(),
    dueEpochDay = due?.toEpochDay(),
    done = done,
    completedEpochDay = completedOn?.toEpochDay(),
    createdAt = if (createdAt == 0L) now() else createdAt,
)
