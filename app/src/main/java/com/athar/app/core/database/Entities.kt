package com.athar.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dates are stored as epoch days; enums as their names (stable across reorderings). */
@Entity(tableName = "counters")
data class CounterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val direction: String,
    val tone: String,
    val calendar: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "tasks",
    indices = [Index("done"), Index("due_epoch_day")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "due_epoch_day") val dueEpochDay: Long?,
    val done: Boolean,
    @ColumnInfo(name = "completed_epoch_day") val completedEpochDay: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
