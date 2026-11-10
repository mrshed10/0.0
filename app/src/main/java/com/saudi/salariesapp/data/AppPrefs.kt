package com.saudi.salariesapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** User-chosen widget colours and content. Colours are ARGB ints. */
data class WidgetSettings(
    val backgroundColor: Int = 0xFF007A5E.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val iconColor: Int = 0xFFFFD54F.toInt(),
    /** Event ids pinned to the widget. Empty = show the nearest events automatically. */
    val pinnedIds: List<String> = emptyList(),
    /** Maximum rows shown on a large widget. */
    val maxItems: Int = 4,
    val showHijriHeader: Boolean = true,
)

/**
 * Local storage (SharedPreferences) for custom countdowns, widget colours and reminder toggles.
 * Shared by the app, the widget and the reminder receivers.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("salaries_app", Context.MODE_PRIVATE)

    // ---- Custom countdowns ----------------------------------------------------------------

    fun loadCountdowns(): List<EventModel> {
        val raw = prefs.getString(KEY_COUNTDOWNS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EventModel(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    target = LocalDateTime.parse(o.getString("target")),
                    category = Category.CUSTOM,
                    colorHex = o.optString("color", Category.CUSTOM.defaultColorHex),
                    calendarType = runCatching { CalendarType.valueOf(o.optString("calendar")) }
                        .getOrDefault(CalendarType.GREGORIAN),
                    isCustom = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCountdowns(list: List<EventModel>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("target", e.target.toString())
                    .put("color", e.colorHex)
                    .put("calendar", e.calendarType.name)
            )
        }
        prefs.edit().putString(KEY_COUNTDOWNS, arr.toString()).apply()
    }

    // ---- Widget -----------------------------------------------------------------------------

    fun loadWidgetSettings(): WidgetSettings {
        val d = WidgetSettings()
        return WidgetSettings(
            backgroundColor = prefs.getInt(KEY_W_BG, d.backgroundColor),
            textColor = prefs.getInt(KEY_W_TEXT, d.textColor),
            iconColor = prefs.getInt(KEY_W_ICON, d.iconColor),
            pinnedIds = prefs.getString(KEY_W_PINNED, null)
                ?.split(',')?.filter { it.isNotBlank() } ?: d.pinnedIds,
            maxItems = prefs.getInt(KEY_W_MAX, d.maxItems),
            showHijriHeader = prefs.getBoolean(KEY_W_HEADER, d.showHijriHeader),
        )
    }

    fun saveWidgetSettings(s: WidgetSettings) {
        prefs.edit()
            .putInt(KEY_W_BG, s.backgroundColor)
            .putInt(KEY_W_TEXT, s.textColor)
            .putInt(KEY_W_ICON, s.iconColor)
            .putString(KEY_W_PINNED, s.pinnedIds.joinToString(","))
            .putInt(KEY_W_MAX, s.maxItems)
            .putBoolean(KEY_W_HEADER, s.showHijriHeader)
            .apply()
    }

    // ---- Reminders --------------------------------------------------------------------------

    fun loadReminderIds(): Set<String> = prefs.getStringSet(KEY_REMINDERS, emptySet())!!.toSet()

    fun saveReminderIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_REMINDERS, ids).apply()
    }

    // ---- Theme ------------------------------------------------------------------------------

    /** null = follow system, true = dark, false = light. */
    fun loadDarkMode(): Boolean? =
        if (!prefs.contains(KEY_DARK)) null else prefs.getBoolean(KEY_DARK, false)

    fun saveDarkMode(value: Boolean?) {
        prefs.edit().apply { if (value == null) remove(KEY_DARK) else putBoolean(KEY_DARK, value) }.apply()
    }

    /** Built-in + custom events, nearest first. */
    fun allEvents(today: java.time.LocalDate = java.time.LocalDate.now()): List<EventModel> =
        com.saudi.salariesapp.data.allEvents(today, loadCountdowns())

    private companion object {
        const val KEY_COUNTDOWNS = "custom_countdowns"
        const val KEY_W_BG = "widget_bg"
        const val KEY_W_TEXT = "widget_text"
        const val KEY_W_ICON = "widget_icon"
        const val KEY_W_PINNED = "widget_pinned"
        const val KEY_W_MAX = "widget_max"
        const val KEY_W_HEADER = "widget_header"
        const val KEY_REMINDERS = "reminders"
        const val KEY_DARK = "dark_mode"
    }
}
