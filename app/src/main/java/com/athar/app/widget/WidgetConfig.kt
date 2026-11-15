package com.athar.app.widget

import androidx.annotation.StringRes
import com.athar.app.R
import org.json.JSONObject

/** What a widget shows. Types marked [needsItem] point at one record (e.g. a counter). */
enum class WidgetType(@StringRes val label: Int, val emoji: String, val needsItem: Boolean = false) {
    NextPrayer(R.string.widget_type_prayer, "🕌"),
    Counter(R.string.widget_type_counter, "⏳", needsItem = true),
    Salary(R.string.widget_type_salary, "💰"),
    TodayTasks(R.string.widget_type_tasks, "✅"),
    Habit(R.string.widget_type_habit, "🔥", needsItem = true),
    Habits(R.string.widget_type_habits, "🔥"),
    Debts(R.string.widget_type_debts, "💼"),
    DebtPerson(R.string.widget_type_debt_person, "👤", needsItem = true),
    Tasbih(R.string.widget_type_tasbih, "📿"),
    Verse(R.string.widget_type_verse, "📖"),
    Date(R.string.widget_type_date, "📅"),
    Schedule(R.string.widget_type_schedule, "🗓️"),
    AnalogClock(R.string.widget_type_analog, "🕰️"),
    DigitalClock(R.string.widget_type_digital, "⌚"),
    Dhikr(R.string.widget_type_dhikr, "📿"),
    WideClock(R.string.widget_type_wide_clock, "🕰️"),
    WorldClock(R.string.widget_type_world_clock, "🌍"),
    BigClock(R.string.widget_type_big_clock, "🔢"),
    YearDots(R.string.widget_type_year_dots, "⚪"),
    MonthCalendar(R.string.widget_type_calendar, "📆"),
    CalligraphyDay(R.string.widget_type_calligraphy_day, "🖋️"),
    CalligraphyMonth(R.string.widget_type_calligraphy_month, "🖋️"),
    ;

    /** Redrawn every minute (bitmap clocks). */
    val minuteClock: Boolean get() = this == BigClock
}

/**
 * [itemId] = -1 means "automatic" (e.g. the nearest salary).
 * [extra] holds type-specific settings, e.g. world-clock city ids "brussels,dubai".
 */
data class WidgetContent(val type: WidgetType, val itemId: Long = -1, val extra: String = "")

enum class BackgroundKind { Color, Gradient, Image }

/** What tapping the widget does. */
enum class ClickBehavior { None, Quick, OpenApp }

/** How dates are written on the widget: "Sunday 15 November" or "2026/11/15". */
enum class DateStyle { Words, Numeric }

/** Full visual control: transparency, colours, gradient, image + blur, corners. Colours are ARGB ints. */
data class WidgetStyle(
    val kind: BackgroundKind = BackgroundKind.Gradient,
    val color1: Int = 0xFF0B1026.toInt(),
    val color2: Int = 0xFF3A3F9E.toInt(),
    /** Gradient direction in degrees (0 = left→right, 90 = top→bottom). */
    val angle: Int = 135,
    /** Background opacity 0–100 (0 = fully transparent). */
    val opacity: Int = 60,
    val imagePath: String? = null,
    /** Image blur radius 0–25. */
    val blur: Int = 12,
    /** Dark/colour veil over the image 0–100, for legibility. */
    val dim: Int = 35,
    val cornerDp: Int = 24,
    val border: Boolean = true,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFE6C27A.toInt(),
    /** Font id for the widget text; null = system font. */
    val fontId: String? = null,
    val dateStyle: DateStyle = DateStyle.Words,
    val click: ClickBehavior = ClickBehavior.Quick,
    /** Design variant (e.g. light/dark dial, year/month dots, Arabic/English calligraphy). */
    val variant: Int = 0,
    /** Use Arabic-Indic digits (١٢٣) in bitmap clocks. */
    val arabicDigits: Boolean = false,
)

data class WidgetConfig(val content: WidgetContent, val style: WidgetStyle = WidgetStyle())

object WidgetPresets {
    val glassNight = WidgetStyle()
    val glassClear = WidgetStyle(kind = BackgroundKind.Color, color1 = 0xFF000000.toInt(), opacity = 25)
    val frost = WidgetStyle(
        kind = BackgroundKind.Gradient, color1 = 0xFFFFFFFF.toInt(), color2 = 0xFFD8E2FF.toInt(), opacity = 45,
        textColor = 0xFF0B1026.toInt(), accentColor = 0xFF3A3F9E.toInt(),
    )
    val gold = WidgetStyle(
        kind = BackgroundKind.Gradient, color1 = 0xFF3B2A12.toInt(), color2 = 0xFFE6C27A.toInt(), opacity = 75,
        accentColor = 0xFFFFF1CF.toInt(),
    )
    val aurora = WidgetStyle(kind = BackgroundKind.Gradient, color1 = 0xFF2EC4B6.toInt(), color2 = 0xFF8B7CF6.toInt(), opacity = 70)
    val invisible = WidgetStyle(kind = BackgroundKind.Color, opacity = 0, border = false)

    val all = listOf(glassNight, glassClear, frost, gold, aurora, invisible)
}

object WidgetJson {
    fun encode(config: WidgetConfig): String = JSONObject()
        .put("type", config.content.type.name)
        .put("item", config.content.itemId)
        .put("extra", config.content.extra)
        .put("style", encodeStyle(config.style))
        .toString()

    fun decode(raw: String?): WidgetConfig? = runCatching {
        val o = JSONObject(raw ?: return null)
        val type = WidgetType.entries.firstOrNull { it.name == o.getString("type") } ?: return null
        WidgetConfig(WidgetContent(type, o.optLong("item", -1), o.optString("extra", "")), decodeStyle(o.optJSONObject("style")))
    }.getOrNull()

    private fun encodeStyle(s: WidgetStyle) = JSONObject()
        .put("kind", s.kind.name)
        .put("c1", s.color1).put("c2", s.color2).put("angle", s.angle)
        .put("opacity", s.opacity)
        .put("image", s.imagePath ?: JSONObject.NULL)
        .put("blur", s.blur).put("dim", s.dim)
        .put("corner", s.cornerDp).put("border", s.border)
        .put("text", s.textColor).put("accent", s.accentColor)
        .put("font", s.fontId ?: JSONObject.NULL)
        .put("date", s.dateStyle.name)
        .put("click", s.click.name)
        .put("variant", s.variant)
        .put("arabicDigits", s.arabicDigits)

    private fun decodeStyle(o: JSONObject?): WidgetStyle {
        val d = WidgetStyle()
        if (o == null) return d
        return WidgetStyle(
            kind = BackgroundKind.entries.firstOrNull { it.name == o.optString("kind") } ?: d.kind,
            color1 = o.optInt("c1", d.color1),
            color2 = o.optInt("c2", d.color2),
            angle = o.optInt("angle", d.angle),
            opacity = o.optInt("opacity", d.opacity).coerceIn(0, 100),
            imagePath = if (o.isNull("image")) null else o.optString("image"),
            blur = o.optInt("blur", d.blur).coerceIn(0, 25),
            dim = o.optInt("dim", d.dim).coerceIn(0, 100),
            cornerDp = o.optInt("corner", d.cornerDp).coerceIn(0, 48),
            border = o.optBoolean("border", d.border),
            textColor = o.optInt("text", d.textColor),
            accentColor = o.optInt("accent", d.accentColor),
            fontId = if (o.isNull("font")) null else o.optString("font"),
            dateStyle = DateStyle.entries.firstOrNull { it.name == o.optString("date") } ?: d.dateStyle,
            click = ClickBehavior.entries.firstOrNull { it.name == o.optString("click") } ?: d.click,
            variant = o.optInt("variant", d.variant).coerceIn(0, 5),
            arabicDigits = o.optBoolean("arabicDigits", d.arabicDigits),
        )
    }
}
