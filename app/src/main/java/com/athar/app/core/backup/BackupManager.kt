package com.athar.app.core.backup

import androidx.room.withTransaction
import com.athar.app.core.database.AtharDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads all tables into a [BackupData] and restores one (replacing everything). */
class BackupManager(private val db: AtharDatabase) {

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        db.withTransaction {
            BackupData(
                counters = db.counterDao().all(),
                tasks = db.taskDao().all(),
                habits = db.habitDao().allHabits(),
                habitLogs = db.habitDao().allLogs(),
                people = db.debtDao().allPeople(),
                debtEntries = db.debtDao().allEntries(),
                salaries = db.plannerDao().allSalaries(),
                reminders = db.plannerDao().allReminders(),
            )
        }
    }

    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.habitDao().clearLogs()
            db.habitDao().clearHabits()
            db.debtDao().clearEntries()
            db.debtDao().clearPeople()
            db.counterDao().clear()
            db.taskDao().clear()
            db.plannerDao().clearSalaries()
            db.plannerDao().clearReminders()

            db.counterDao().insertAll(data.counters)
            db.taskDao().insertAll(data.tasks)
            db.habitDao().insertHabits(data.habits)
            db.habitDao().insertLogs(data.habitLogs)
            db.debtDao().insertPeople(data.people)
            db.debtDao().insertEntries(data.debtEntries)
            db.plannerDao().insertSalaries(data.salaries)
            db.plannerDao().insertReminders(data.reminders)
        }
    }
}
