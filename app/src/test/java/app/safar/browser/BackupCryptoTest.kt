package app.safar.browser

import app.safar.browser.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoTest {
    private val data = """{"settings":{"adBlock":true},"bookmarks":[]}""".toByteArray()

    @Test fun roundTrip() {
        val enc = BackupCrypto.encrypt(data, "correct horse".toCharArray(), iterations = 1000)
        assertArrayEquals(data, BackupCrypto.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val enc = BackupCrypto.encrypt(data, "pw123456".toCharArray(), iterations = 1000)
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("bookmarks"))
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordFails() {
        val enc = BackupCrypto.encrypt(data, "right-password".toCharArray(), iterations = 1000)
        BackupCrypto.decrypt(enc, "wrong-password".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun tamperingDetected() {
        val enc = BackupCrypto.encrypt(data, "pw123456".toCharArray(), iterations = 1000)
        enc[enc.size - 5] = (enc[enc.size - 5].toInt() xor 0x01).toByte()
        BackupCrypto.decrypt(enc, "pw123456".toCharArray())
    }
}
