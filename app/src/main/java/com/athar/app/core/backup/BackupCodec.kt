package com.athar.app.core.backup

import com.athar.app.core.database.CounterEntity
import com.athar.app.core.database.DebtEntryEntity
import com.athar.app.core.database.HabitEntity
import com.athar.app.core.database.HabitLogEntity
import com.athar.app.core.database.PersonEntity
import com.athar.app.core.database.ReminderEntity
import com.athar.app.core.database.SalarySourceEntity
import com.athar.app.core.database.TaskEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything the app stores, as plain rows. */
data class BackupData(
    val counters: List<CounterEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val debtEntries: List<DebtEntryEntity> = emptyList(),
    val salaries: List<SalarySourceEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    /** Edited adhkar lists (JSON), null if not included. */
    val adhkar: String? = null,
) {
    val itemCount: Int
        get() = counters.size + tasks.size + habits.size + people.size + debtEntries.size + salaries.size + reminders.size
}

sealed class BackupException(message: String) : Exception(message) {
    class InvalidFile : BackupException("Not an Athar backup file")
    class PasswordRequired : BackupException("This backup is encrypted")
    class WrongPassword : BackupException("Wrong password or damaged file")
    class NewerVersion : BackupException("Backup made by a newer app version")
}

/**
 * JSON backup format. With a password, the payload is encrypted with
 * AES-256-GCM using a key derived by PBKDF2-HMAC-SHA256 (random salt + IV).
 */
object BackupCodec {
    const val FORMAT = "athar-backup"
    const val VERSION = 1
    private const val ITERATIONS = 120_000

    fun encode(data: BackupData, password: String? = null): String {
        val payload = toJson(data).toString()
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
        if (password.isNullOrEmpty()) {
            envelope.put("encrypted", false).put("data", JSONObject(payload))
        } else {
            val salt = randomBytes(16)
            val iv = randomBytes(12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            val sealed = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            envelope.put("encrypted", true)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("iterations", ITERATIONS)
                .put("salt", b64(salt))
                .put("iv", b64(iv))
                .put("data", b64(sealed))
        }
        return envelope.toString(2)
    }

    /** True if [raw] is a valid backup that needs a password. */
    fun isEncrypted(raw: String): Boolean = envelope(raw).optBoolean("encrypted", false)

    fun decode(raw: String, password: String? = null): BackupData {
        val envelope = envelope(raw)
        try {
            val payload = if (envelope.optBoolean("encrypted", false)) {
                if (password.isNullOrEmpty()) throw BackupException.PasswordRequired()
                val salt = unb64(envelope.getString("salt"))
                val iv = unb64(envelope.getString("iv"))
                val iterations = envelope.optInt("iterations", ITERATIONS)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
                val plain = try {
                    cipher.doFinal(unb64(envelope.getString("data")))
                } catch (e: GeneralSecurityException) {
                    throw BackupException.WrongPassword()
                }
                JSONObject(String(plain, Charsets.UTF_8))
            } else {
                envelope.getJSONObject("data")
            }
            return fromJson(payload)
        } catch (e: JSONException) {
            throw BackupException.InvalidFile()
        } catch (e: IllegalArgumentException) {
            throw BackupException.InvalidFile()
        }
    }

    private fun envelope(raw: String): JSONObject {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw BackupException.InvalidFile()
        }
        if (json.optString("format") != FORMAT) throw BackupException.InvalidFile()
        if (json.optInt("version", 0) > VERSION) throw BackupException.NewerVersion()
        return json
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    // --- rows <-> JSON ---

    private fun toJson(d: BackupData) = JSONObject()
        .put("adhkar", d.adhkar ?: JSONObject.NULL)
        .put("counters", array(d.counters) {
            JSONObject().put("id", it.id).put("title", it.title).put("date", it.dateEpochDay)
                .put("direction", it.direction).put("tone", it.tone).put("calendar", it.calendar)
                .put("createdAt", it.createdAt)
        })
        .put("tasks", array(d.tasks) {
            JSONObject().put("id", it.id).put("title", it.title).put("due", it.dueEpochDay ?: JSONObject.NULL)
                .put("done", it.done).put("completed", it.completedEpochDay ?: JSONObject.NULL)
                .put("createdAt", it.createdAt)
        })
        .put("habits", array(d.habits) {
            JSONObject().put("id", it.id).put("title", it.title).put("icon", it.icon).put("tone", it.tone)
                .put("createdAt", it.createdAt)
        })
        .put("habitLogs", array(d.habitLogs) {
            JSONObject().put("habitId", it.habitId).put("day", it.epochDay)
        })
        .put("people", array(d.people) {
            JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("createdAt", it.createdAt)
        })
        .put("debtEntries", array(d.debtEntries) {
            JSONObject().put("id", it.id).put("personId", it.personId).put("type", it.type)
                .put("amount", it.amountMinor).put("note", it.note).put("day", it.epochDay)
                .put("createdAt", it.createdAt)
        })
        .put("salaries", array(d.salaries) {
            JSONObject().put("id", it.id).put("name", it.name).put("payDay", it.payDay).put("calendar", it.calendar)
                .put("shift", it.shift).put("remind", it.remindDaysBefore).put("tone", it.tone ?: JSONObject.NULL)
                .put("createdAt", it.createdAt)
        })
        .put("reminders", array(d.reminders) {
            JSONObject().put("id", it.id).put("title", it.title).put("day", it.epochDay).put("minute", it.minuteOfDay)
                .put("repeat", it.repeatRule).put("calendar", it.calendar).put("tone", it.tone ?: JSONObject.NULL)
                .put("enabled", it.enabled).put("createdAt", it.createdAt)
        })

    private fun fromJson(o: JSONObject) = BackupData(
        adhkar = if (o.has("adhkar") && !o.isNull("adhkar")) o.getString("adhkar") else null,
        counters = list(o, "counters") {
            CounterEntity(
                it.getLong("id"), it.getString("title"), it.getLong("date"), it.getString("direction"),
                it.getString("tone"), it.getString("calendar"), it.getLong("createdAt"),
            )
        },
        tasks = list(o, "tasks") {
            TaskEntity(
                it.getLong("id"), it.getString("title"), it.optLongOrNull("due"), it.getBoolean("done"),
                it.optLongOrNull("completed"), it.getLong("createdAt"),
            )
        },
        habits = list(o, "habits") {
            HabitEntity(it.getLong("id"), it.getString("title"), it.getString("icon"), it.getString("tone"), it.getLong("createdAt"))
        },
        habitLogs = list(o, "habitLogs") { HabitLogEntity(it.getLong("habitId"), it.getLong("day")) },
        people = list(o, "people") {
            PersonEntity(it.getLong("id"), it.getString("name"), it.optString("phone", ""), it.getLong("createdAt"))
        },
        debtEntries = list(o, "debtEntries") {
            DebtEntryEntity(
                it.getLong("id"), it.getLong("personId"), it.getString("type"), it.getLong("amount"),
                it.optString("note", ""), it.getLong("day"), it.getLong("createdAt"),
            )
        },
        salaries = list(o, "salaries") {
            SalarySourceEntity(
                it.getLong("id"), it.getString("name"), it.getInt("payDay"), it.getString("calendar"),
                it.getString("shift"), it.getInt("remind"), it.optStringOrNull("tone"), it.getLong("createdAt"),
            )
        },
        reminders = list(o, "reminders") {
            ReminderEntity(
                it.getLong("id"), it.getString("title"), it.getLong("day"), it.getInt("minute"),
                it.getString("repeat"), it.getString("calendar"), it.optStringOrNull("tone"),
                it.getBoolean("enabled"), it.getLong("createdAt"),
            )
        },
    )

    private fun <T> array(items: List<T>, map: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> items.forEach { array.put(map(it)) } }

    private fun <T> list(o: JSONObject, key: String, map: (JSONObject) -> T): List<T> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { map(array.getJSONObject(it)) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
