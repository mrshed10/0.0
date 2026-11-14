package com.athar.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CounterEntity::class,
        TaskEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        PersonEntity::class,
        DebtEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AtharDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun debtDao(): DebtDao

    companion object {
        fun build(context: Context): AtharDatabase =
            Room.databaseBuilder(context.applicationContext, AtharDatabase::class.java, "athar.db")
                // v1 was a pre-release test build only; from v2 on, write real migrations.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
