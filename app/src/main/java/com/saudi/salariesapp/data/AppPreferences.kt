package com.saudi.salariesapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** إعدادات ألوان الودجت (خلفية، نص، أيقونات) + حجم الخط والشفافية. */
data class WidgetStyle(
    val backgroundHex: String = "#007A5E",
    val textHex: String = "#FFFFFF",
    val iconHex: String = "#FFD54F",
    val backgroundAlpha: Float = 1f,
    val textScale: Float = 1f,
)

/**
 * التخزين المحلي الكامل للتطبيق عبر SharedPreferences:
 * العدادات الشخصية، تخصيص مواعيد الخدمات، التنبيهات، ما يظهر في الودجت، وألوانه.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("saudi_salaries_prefs", Context.MODE_PRIVATE)

    // ---------- العدادات الشخصية ----------
    fun loadCountdowns(): List<CustomCountdown> {
        val raw = prefs.getString(KEY_COUNTDOWNS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomCountdown(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    target = LocalDateTime.parse(o.getString("target")),
                    colorHex = o.optString("color", "#007A5E"),
                    enteredAsHijri = o.optBoolean("hijri", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCountdowns(list: List<CustomCountdown>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("target", it.target.toString())
                    .put("color", it.colorHex)
                    .put("hijri", it.enteredAsHijri)
            )
        }
        prefs.edit().putString(KEY_COUNTDOWNS, arr.toString()).apply()
    }

    // ---------- مواعيد الخدمات ----------
    fun paymentDay(service: PaymentService): Int =
        prefs.getInt("day_${service.id}", service.defaultDay)

    fun setPaymentDay(serviceId: String, day: Int) =
        prefs.edit().putInt("day_$serviceId", day.coerceIn(1, 31)).apply()

    fun isNotificationEnabled(serviceId: String): Boolean =
        prefs.getBoolean("notify_$serviceId", false)

    fun setNotificationEnabled(serviceId: String, enabled: Boolean) =
        prefs.edit().putBoolean("notify_$serviceId", enabled).apply()

    // ---------- محتوى الودجت ----------
    /** معرّفات الخدمات/العدادات المثبّتة في الودجت. إن كانت فارغة يعرض الودجت أقرب المواعيد. */
    fun widgetPinned(): Set<String> = prefs.getStringSet(KEY_WIDGET_PINNED, emptySet()) ?: emptySet()

    fun setWidgetPinned(id: String, pinned: Boolean) {
        val set = widgetPinned().toMutableSet()
        if (pinned) set += id else set -= id
        prefs.edit().putStringSet(KEY_WIDGET_PINNED, set).apply()
    }

    fun widgetStyle(): WidgetStyle = WidgetStyle(
        backgroundHex = prefs.getString("w_bg", null) ?: WidgetStyle().backgroundHex,
        textHex = prefs.getString("w_text", null) ?: WidgetStyle().textHex,
        iconHex = prefs.getString("w_icon", null) ?: WidgetStyle().iconHex,
        backgroundAlpha = prefs.getFloat("w_alpha", 1f),
        textScale = prefs.getFloat("w_scale", 1f),
    )

    fun saveWidgetStyle(style: WidgetStyle) = prefs.edit()
        .putString("w_bg", style.backgroundHex)
        .putString("w_text", style.textHex)
        .putString("w_icon", style.iconHex)
        .putFloat("w_alpha", style.backgroundAlpha)
        .putFloat("w_scale", style.textScale)
        .apply()

    // ---------- المظهر ----------
    /** 0 = حسب النظام، 1 = فاتح، 2 = داكن */
    fun themeMode(): Int = prefs.getInt("theme_mode", 0)
    fun setThemeMode(mode: Int) = prefs.edit().putInt("theme_mode", mode).apply()

    private companion object {
        const val KEY_COUNTDOWNS = "custom_countdowns"
        const val KEY_WIDGET_PINNED = "widget_pinned"
    }
}
