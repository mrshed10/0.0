package com.athar.app.core.backup

import com.athar.app.core.database.CounterEntity
import com.athar.app.core.database.DebtEntryEntity
import com.athar.app.core.database.HabitEntity
import com.athar.app.core.database.HabitLogEntity
import com.athar.app.core.database.PersonEntity
import com.athar.app.core.database.ReminderEntity
import com.athar.app.core.database.SalarySourceEntity
import com.athar.app.core.database.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val data = BackupData(
        counters = listOf(CounterEntity(1, "رمضان", 20_000, "CountDown", "Gold", "Hijri", 111)),
        tasks = listOf(
            TaskEntity(1, "Pay bill", 20_400, false, null, 222),
            TaskEntity(2, "No date", null, true, 20_401, 223),
        ),
        habits = listOf(HabitEntity(1, "الورد", "Quran", "Rose", 333)),
        habitLogs = listOf(HabitLogEntity(1, 20_400), HabitLogEntity(1, 20_401)),
        people = listOf(PersonEntity(1, "علي", "0500000000", 444)),
        debtEntries = listOf(DebtEntryEntity(1, 1, "Gave", 150_050, "قرض", 20_400, 555)),
        salaries = listOf(
            SalarySourceEntity(1, "رواتب الموظفين", 27, "Gregorian", "Before", 1, null, 666),
            SalarySourceEntity(2, "Hijri pay", 1, "Hijri", "None", -1, "content://media/1", 667),
        ),
        reminders = listOf(ReminderEntity(1, "دواء", 20_400, 540, "Daily", "Gregorian", null, true, 777)),
    )

    @Test
    fun oldBackupWithoutNewTablesStillLoads() {
        val raw = BackupCodec.encode(data.copy(salaries = emptyList(), reminders = emptyList()))
            .replace("\"salaries\"", "\"x_salaries\"").replace("\"reminders\"", "\"x_reminders\"")
        assertEquals(data.copy(salaries = emptyList(), reminders = emptyList()), BackupCodec.decode(raw))
    }

    @Test
    fun plainRoundTrip() {
        val raw = BackupCodec.encode(data)
        assertFalse(BackupCodec.isEncrypted(raw))
        assertEquals(data, BackupCodec.decode(raw))
    }

    @Test
    fun encryptedRoundTrip() {
        val raw = BackupCodec.encode(data, "s3cret")
        assertTrue(BackupCodec.isEncrypted(raw))
        assertFalse("payload must not be readable", raw.contains("رمضان"))
        assertEquals(data, BackupCodec.decode(raw, "s3cret"))
    }

    @Test(expected = BackupException.WrongPassword::class)
    fun wrongPasswordFails() {
        BackupCodec.decode(BackupCodec.encode(data, "s3cret"), "nope")
    }

    @Test(expected = BackupException.PasswordRequired::class)
    fun missingPasswordFails() {
        BackupCodec.decode(BackupCodec.encode(data, "s3cret"))
    }

    @Test(expected = BackupException.InvalidFile::class)
    fun otherJsonIsRejected() {
        BackupCodec.decode("""{"hello": "world"}""")
    }

    @Test(expected = BackupException.InvalidFile::class)
    fun garbageIsRejected() {
        BackupCodec.decode("not json at all")
    }
}
