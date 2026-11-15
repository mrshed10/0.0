package com.athar.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CounterEntity::class,
        TaskEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        PersonEntity::class,
        DebtEntryEntity::class,
        SalarySourceEntity::class,
        ReminderEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AtharDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun debtDao(): DebtDao
    abstract fun plannerDao(): PlannerDao

    companion object {
        /** v2 -> v3: salary sources and reminders. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `salary_sources` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`pay_day` INTEGER NOT NULL, " +
                        "`calendar` TEXT NOT NULL, " +
                        "`shift` TEXT NOT NULL, " +
                        "`remind_days_before` INTEGER NOT NULL, " +
                        "`tone` TEXT, " +
                        "`created_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`epoch_day` INTEGER NOT NULL, " +
                        "`minute_of_day` INTEGER NOT NULL, " +
                        "`repeat_rule` TEXT NOT NULL, " +
                        "`calendar` TEXT NOT NULL, " +
                        "`tone` TEXT, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)",
                )
            }
        }

        /** v3 -> v4: optional address for people in the debts ledger. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `people` ADD COLUMN `address` TEXT")
            }
        }

        fun build(context: Context): AtharDatabase =
            Room.databaseBuilder(context.applicationContext, AtharDatabase::class.java, "athar.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // v1 was a pre-release test build only.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
