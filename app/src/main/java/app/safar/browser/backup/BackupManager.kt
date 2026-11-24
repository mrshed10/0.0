package app.safar.browser.backup

import android.content.Context
import android.net.Uri
import app.safar.browser.data.AppJson
import app.safar.browser.data.Bookmark
import app.safar.browser.data.BookmarkStore
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.HistoryEntry
import app.safar.browser.data.HistoryStore
import app.safar.browser.data.SessionStore
import app.safar.browser.data.SettingsRepository
import app.safar.browser.data.TabSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: BrowserSettings,
    val bookmarks: List<Bookmark>,
    val tabs: List<TabSnapshot>,
    /** السجل (أُضيف في الإصدار 2 من صيغة النسخة؛ النسخ القديمة تُقرأ بقائمة فارغة). */
    val history: List<HistoryEntry> = emptyList(),
)

/**
 * نسخة احتياطية محلية مشفرة: الإعدادات (ومنها DNS الآمن والتكبير لكل موقع ومواقع سطح المكتب)
 * + الإشارات المرجعية + السجل + الجلسة الحالية بكل تبويباتها (مع سجل تنقل كل تبويب)
 * + روابط الإضافات المثبتة. مفاتيح API لا تُضمَّن عمداً.
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val bookmarks: BookmarkStore,
    private val sessions: SessionStore,
    private val history: HistoryStore,
) {
    suspend fun export(uri: Uri, password: CharArray, currentTabs: List<TabSnapshot>) = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            settings = settings.current(), bookmarks = bookmarks.items.value, tabs = currentTabs, history = history.items.value,
        )
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
        if (payload.history.isNotEmpty()) history.set(payload.history)
        payload
    }
}
