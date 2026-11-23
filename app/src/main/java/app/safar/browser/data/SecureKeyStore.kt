package app.safar.browser.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * تخزين مفاتيح API مشفّرة بمفتاح AES داخل Android Keystore (لا يغادر العتاد الآمن).
 * المفاتيح لا تدخل في النسخة الاحتياطية عمداً.
 */
class SecureKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_keys", Context.MODE_PRIVATE)
    private val alias = "safar_api_keys"

    enum class Name { GOOGLE, DEEPL, CLAUDE, GEMINI }

    fun get(name: Name): String? = prefs.getString(name.name, null)?.let { decrypt(it) }

    fun put(name: Name, value: String?) {
        val e = prefs.edit()
        if (value.isNullOrBlank()) e.remove(name.name) else e.putString(name.name, encrypt(value.trim()))
        e.apply()
    }

    fun has(name: Name) = prefs.contains(name.name)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String? = runCatching {
        val bytes = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()
}
