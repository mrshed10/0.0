package com.athar.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CounterEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AtharDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun build(context: Context): AtharDatabase =
            Room.databaseBuilder(context.applicationContext, AtharDatabase::class.java, "athar.db")
                .build()
    }
}
