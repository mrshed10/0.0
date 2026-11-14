package com.athar.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters ORDER BY date_epoch_day ASC")
    fun observeAll(): Flow<List<CounterEntity>>

    @Upsert
    suspend fun upsert(counter: CounterEntity): Long

    @Query("DELETE FROM counters WHERE id = :id")
    suspend fun delete(id: Long)

    // Backup
    @Query("SELECT * FROM counters")
    suspend fun all(): List<CounterEntity>

    @Insert
    suspend fun insertAll(items: List<CounterEntity>)

    @Query("DELETE FROM counters")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY created_at ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done, completed_epoch_day = :completedEpochDay WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedEpochDay: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteCompleted()

    // Backup
    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Insert
    suspend fun insertAll(items: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY created_at ASC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habit_id = :habitId AND epoch_day = :epochDay")
    suspend fun deleteLog(habitId: Long, epochDay: Long)

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habit_id = :habitId AND epoch_day = :epochDay")
    suspend fun countLog(habitId: Long, epochDay: Long): Int

    // Backup
    @Query("SELECT * FROM habits")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs")
    suspend fun allLogs(): List<HabitLogEntity>

    @Insert
    suspend fun insertHabits(items: List<HabitEntity>)

    @Insert
    suspend fun insertLogs(items: List<HabitLogEntity>)

    @Query("DELETE FROM habit_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM habits")
    suspend fun clearHabits()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE ASC")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM debt_entries ORDER BY epoch_day DESC, created_at DESC")
    fun observeEntries(): Flow<List<DebtEntryEntity>>

    @Upsert
    suspend fun upsertPerson(person: PersonEntity): Long

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Upsert
    suspend fun upsertEntry(entry: DebtEntryEntity): Long

    @Query("DELETE FROM debt_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    // Backup
    @Query("SELECT * FROM people")
    suspend fun allPeople(): List<PersonEntity>

    @Query("SELECT * FROM debt_entries")
    suspend fun allEntries(): List<DebtEntryEntity>

    @Insert
    suspend fun insertPeople(items: List<PersonEntity>)

    @Insert
    suspend fun insertEntries(items: List<DebtEntryEntity>)

    @Query("DELETE FROM debt_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM people")
    suspend fun clearPeople()
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM salary_sources ORDER BY created_at ASC")
    fun observeSalaries(): Flow<List<SalarySourceEntity>>

    @Upsert
    suspend fun upsertSalary(source: SalarySourceEntity): Long

    @Query("DELETE FROM salary_sources WHERE id = :id")
    suspend fun deleteSalary(id: Long)

    @Query("SELECT COUNT(*) FROM salary_sources")
    suspend fun salaryCount(): Int

    @Query("SELECT * FROM reminders ORDER BY created_at ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setReminderEnabled(id: Long, enabled: Boolean)

    // Backup
    @Query("SELECT * FROM salary_sources")
    suspend fun allSalaries(): List<SalarySourceEntity>

    @Query("SELECT * FROM reminders")
    suspend fun allReminders(): List<ReminderEntity>

    @Insert
    suspend fun insertSalaries(items: List<SalarySourceEntity>)

    @Insert
    suspend fun insertReminders(items: List<ReminderEntity>)

    @Query("DELETE FROM salary_sources")
    suspend fun clearSalaries()

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()
}
