package com.athar.app.core.database

import androidx.room.Dao
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
}
