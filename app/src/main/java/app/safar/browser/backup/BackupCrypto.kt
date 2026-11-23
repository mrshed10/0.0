package app.safar.browser.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * تشفير النسخة الاحتياطية بكلمة مرور (قابلة للنقل بين الأجهزة):
 * PBKDF2-HMAC-SHA256 (210k تكرار) -> مفتاح AES-256 -> AES-GCM (مصادقة + سرية).
 * الصيغة: "SFBK" | v=1 | iterations(4) | salt(16) | iv(12) | ciphertext+tag
 */
object BackupCrypto {
    private val MAGIC = "SFBK".toByteArray()
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 210_000

    class WrongPasswordException : Exception("كلمة المرور غير صحيحة أو الملف تالف")

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val ct = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 1 + 4 + salt.size + iv.size + ct.size)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).put(ct).array()
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(4).also { buf.get(it) }
        require(magic.contentEquals(MAGIC)) { "ليس ملف نسخة احتياطية لسفر" }
        require(buf.get() == VERSION) { "إصدار غير مدعوم" }
        val iterations = buf.int
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return try { cipher.doFinal(ct) } catch (e: AEADBadTagException) { throw WrongPasswordException() }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
