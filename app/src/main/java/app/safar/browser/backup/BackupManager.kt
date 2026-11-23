package app.safar.browser.backup

import android.content.Context
import android.net.Uri
import app.safar.browser.data.AppJson
import app.safar.browser.data.Bookmark
import app.safar.browser.data.BookmarkStore
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.SessionStore
import app.safar.browser.data.SettingsRepository
import app.safar.browser.data.TabSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: BrowserSettings,
    val bookmarks: List<Bookmark>,
    val tabs: List<TabSnapshot>,
)

/**
 * نسخة احتياطية محلية مشفرة: الإعدادات + الإشارات المرجعية + الجلسة (التبويبات غير الخاصة)
 * + روابط الإضافات المثبتة (ضمن الإعدادات). مفاتيح API لا تُضمَّن عمداً.
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val bookmarks: BookmarkStore,
    private val sessions: SessionStore,
) {
    suspend fun export(uri: Uri, password: CharArray, currentTabs: List<TabSnapshot>) = withContext(Dispatchers.IO) {
        val payload = BackupPayload(settings = settings.current(), bookmarks = bookmarks.items.value, tabs = currentTabs)
        val json = AppJson.encodeToString(BackupPayload.serializer(), payload).toByteArray()
        val enc = BackupCrypto.encrypt(json, password)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(enc) }
    }

    /** يُرجع الحمولة بعد تطبيق الإعدادات والإشارات والجلسة. */
    suspend fun import(uri: Uri, password: CharArray): BackupPayload = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val json = String(BackupCrypto.decrypt(data, password))
        val payload = AppJson.decodeFromString(BackupPayload.serializer(), json)
        settings.replace(payload.settings.copy(onboardingDone = true))
        bookmarks.set(payload.bookmarks)
        sessions.set(payload.tabs)
        payload
    }
}
