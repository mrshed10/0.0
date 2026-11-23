package app.safar.browser.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** نخزن الإعدادات كوثيقة JSON واحدة: أبسط للترحيل والنسخ الاحتياطي. */
class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")

    val settings: Flow<BrowserSettings> = context.dataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun current(): BrowserSettings = settings.first()

    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        context.dataStore.edit { prefs ->
            prefs[key] = AppJson.encodeToString(BrowserSettings.serializer(), transform(decode(prefs[key])))
        }
    }

    suspend fun replace(settings: BrowserSettings) = update { settings }

    private fun decode(raw: String?): BrowserSettings =
        raw?.let { runCatching { AppJson.decodeFromString(BrowserSettings.serializer(), it) }.getOrNull() }
            ?: BrowserSettings()
}
