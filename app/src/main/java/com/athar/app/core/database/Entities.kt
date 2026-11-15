package com.athar.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val tone: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One row per day a habit was done. */
@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habit_id", "epoch_day"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habit_id")],
)
data class HabitLogEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Added in v4 (nullable so the upgrade is a simple ALTER TABLE). */
    @ColumnInfo(name = "address") val address: String? = null,
)

/** Amounts in minor units (halalas) to avoid rounding errors. */
@Entity(
    tableName = "debt_entries",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("person_id")],
)
data class DebtEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long,
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val note: String,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "salary_sources")
data class SalarySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "pay_day") val payDay: Int,
    val calendar: String,
    val shift: String,
    @ColumnInfo(name = "remind_days_before") val remindDaysBefore: Int,
    val tone: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    @ColumnInfo(name = "repeat_rule") val repeatRule: String,
    val calendar: String,
    val tone: String?,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
