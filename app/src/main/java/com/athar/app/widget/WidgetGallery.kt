package com.athar.app.widget

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.glass.HideBottomBar
import com.athar.app.core.designsystem.theme.AtharColors
import kotlinx.coroutines.flow.first

enum class GalleryTab(@StringRes val label: Int) {
    Mine(R.string.gallery_mine),
    Nothing(R.string.gallery_nothing),
    New(R.string.gallery_new),
    Clock(R.string.gallery_clock),
    Digital(R.string.gallery_digital),
    Date(R.string.gallery_date),
    Calligraphy(R.string.gallery_calligraphy),
    Prayer(R.string.gallery_prayer),
    Counters(R.string.gallery_counters),
    Habits(R.string.gallery_habits),
    Debts(R.string.gallery_debts),
    Salaries(R.string.gallery_salaries),
    Quotes(R.string.gallery_quotes),
}

/** A ready-made widget design shown in the gallery. */
data class WidgetTemplate(
    val id: String,
    val tab: GalleryTab,
    @StringRes val name: Int,
    val config: WidgetConfig,
    val wide: Boolean,
    val isNew: Boolean = false,
    /** Name of a saved design (instead of [name]). */
    val title: String? = null,
    /** Set for designs the user saved ("My designs"). */
    val savedId: String? = null,
)

object WidgetTemplates {
    private fun t(
        id: String,
        tab: GalleryTab,
        name: Int,
        type: WidgetType,
        style: WidgetStyle,
        wide: Boolean,
        isNew: Boolean = false,
    ) = WidgetTemplate(id, tab, name, WidgetConfig(WidgetContent(type), style), wide, isNew)

    private val night = WidgetPresets.glassNight
    private val gold = WidgetPresets.gold
    private val frost = WidgetPresets.frost
    private val aurora = WidgetPresets.aurora
    private val clear = WidgetPresets.glassClear
    private val glow = WidgetPresets.glow

    private val dark = WidgetStyle(kind = BackgroundKind.Gradient, color1 = 0xFF1C1C1E.toInt(), color2 = 0xFF2C2C2E.toInt(), opacity = 92, accentColor = 0xFFE6C27A.toInt())

    /** Nothing-inspired: deep black, faint dot grid, white dots, red accent. */
    val nothingDark = WidgetStyle(
        kind = BackgroundKind.DotGrid, color1 = 0xFF000000.toInt(), opacity = 100, border = false, cornerDp = 30,
        textColor = 0xFFFFFFFF.toInt(), accentColor = 0xFFD71921.toInt(),
    )
    val nothingLight = nothingDark.copy(color1 = 0xFFF1F1F1.toInt(), textColor = 0xFF111111.toInt())

    val all = listOf(
        // Nothing style
        t("nothing_clock", GalleryTab.Nothing, R.string.widget_type_nothing_clock, WidgetType.NothingClock, nothingDark, wide = false, isNew = true),
        t("nothing_clock_wide", GalleryTab.Nothing, R.string.tpl_nothing_clock_wide, WidgetType.NothingClock, nothingDark.copy(variant = 1), wide = true, isNew = true),
        t("nothing_clock_light", GalleryTab.Nothing, R.string.tpl_nothing_light, WidgetType.NothingClock, nothingLight, wide = false),
        t("nothing_glyph", GalleryTab.Nothing, R.string.widget_type_nothing_glyph, WidgetType.NothingGlyph, nothingDark, wide = false, isNew = true),
        t("nothing_glyph_year", GalleryTab.Nothing, R.string.tpl_nothing_year, WidgetType.NothingGlyph, nothingDark.copy(variant = 1), wide = false),
        t("nothing_prayer", GalleryTab.Nothing, R.string.widget_type_nothing_prayer, WidgetType.NothingPrayer, nothingDark, wide = true, isNew = true),
        t("nothing_counter", GalleryTab.Nothing, R.string.widget_type_nothing_counter, WidgetType.NothingCounter, nothingDark, wide = false),
        t("nothing_date", GalleryTab.Nothing, R.string.widget_type_nothing_date, WidgetType.NothingDate, nothingDark, wide = false),
        t("nothing_hijri", GalleryTab.Nothing, R.string.tpl_nothing_hijri, WidgetType.NothingDate, nothingLight.copy(variant = 1), wide = false),
        // Glowing dots + animated prayer countdowns
        t("prayer_ring_glow", GalleryTab.Nothing, R.string.tpl_prayer_ring, WidgetType.NextPrayer, glow.copy(variant = PrayerPulse.RING), wide = false, isNew = true),
        t("prayer_iqama_glow", GalleryTab.Nothing, R.string.tpl_prayer_iqama, WidgetType.NextPrayer, glow.copy(variant = PrayerPulse.IQAMA), wide = true, isNew = true),
        t("prayer_bar_glow", GalleryTab.Nothing, R.string.tpl_prayer_bar, WidgetType.NextPrayer, glow.copy(variant = PrayerPulse.BAR), wide = true, isNew = true),
        t("nothing_glow_clock", GalleryTab.Nothing, R.string.tpl_glow_clock, WidgetType.NothingClock, glow, wide = false, isNew = true),
        t("nothing_glow_date", GalleryTab.Nothing, R.string.tpl_glow_date, WidgetType.NothingDate, glow.copy(accentColor = 0xFFE6C27A.toInt()), wide = false, isNew = true),
        t("glow_big_clock", GalleryTab.Digital, R.string.tpl_glow_big_clock, WidgetType.BigClock, glow.copy(fontId = "bebasneue"), wide = true, isNew = true),
        // From the design file: clocks & world time
        t("wide_clock", GalleryTab.Clock, R.string.widget_type_wide_clock, WidgetType.WideClock, dark, wide = true, isNew = true),
        t("wide_clock_frost", GalleryTab.Clock, R.string.tpl_wide_frost, WidgetType.WideClock, frost.copy(variant = 1), wide = true),
        t("world_side", GalleryTab.Clock, R.string.widget_type_world_clock, WidgetType.WorldClock, dark, wide = true, isNew = true),
        t("world_stacked", GalleryTab.Clock, R.string.tpl_world_stacked, WidgetType.WorldClock, dark.copy(variant = 1), wide = false),
        t("big_bebas", GalleryTab.Digital, R.string.tpl_big_condensed, WidgetType.BigClock, dark.copy(fontId = "bebasneue"), wide = false, isNew = true),
        t("big_serif", GalleryTab.Digital, R.string.tpl_big_serif, WidgetType.BigClock, dark.copy(fontId = "abrilfatface"), wide = true),
        t("big_arabic", GalleryTab.Digital, R.string.tpl_big_arabic, WidgetType.BigClock, dark.copy(fontId = "rakkas", arabicDigits = true), wide = true),
        t("big_vertical", GalleryTab.Digital, R.string.tpl_big_vertical, WidgetType.BigClock, dark.copy(fontId = "anton", variant = 1), wide = false, isNew = true),
        // Calendar & percentage
        t("year_dots", GalleryTab.Date, R.string.widget_type_year_dots, WidgetType.YearDots, dark, wide = true, isNew = true),
        t("month_dots", GalleryTab.Date, R.string.tpl_month_dots, WidgetType.YearDots, dark.copy(variant = 1), wide = false),
        t("calendar", GalleryTab.Date, R.string.widget_type_calendar, WidgetType.MonthCalendar, dark, wide = false, isNew = true),
        t("calendar_wide", GalleryTab.Date, R.string.tpl_calendar_wide, WidgetType.MonthCalendar, dark, wide = true),
        t("date_simple", GalleryTab.Date, R.string.tpl_date_simple, WidgetType.Date, dark.copy(variant = 1), wide = false, isNew = true),
        t("date_strip", GalleryTab.Date, R.string.tpl_date_strip, WidgetType.Date, dark.copy(variant = 2), wide = true),
        t("date_hijri_greg", GalleryTab.Date, R.string.tpl_date_hijri_greg, WidgetType.Date, dark.copy(variant = 3), wide = true),
        t("date_dual", GalleryTab.Date, R.string.tpl_date_dual, WidgetType.Date, dark.copy(variant = 4, fontId = "arefruqaa"), wide = true, isNew = true),
        // Arabic calligraphy
        t("cal_day_ar", GalleryTab.Calligraphy, R.string.tpl_cal_day_ar, WidgetType.CalligraphyDay, dark.copy(fontId = "arefruqaa"), wide = true, isNew = true),
        t("cal_day_en", GalleryTab.Calligraphy, R.string.tpl_cal_day_en, WidgetType.CalligraphyDay, dark.copy(fontId = "cinzel", variant = 1), wide = true),
        t("cal_day_ar_sq", GalleryTab.Calligraphy, R.string.tpl_cal_day_ar, WidgetType.CalligraphyDay, dark.copy(fontId = "arefruqaa"), wide = false),
        t("cal_day_en_sq", GalleryTab.Calligraphy, R.string.tpl_cal_day_en_short, WidgetType.CalligraphyDay, dark.copy(fontId = "cinzel", variant = 2), wide = false),
        t("cal_month_g_ar", GalleryTab.Calligraphy, R.string.tpl_cal_month_g_ar, WidgetType.CalligraphyMonth, dark.copy(fontId = "arefruqaa"), wide = true, isNew = true),
        t("cal_month_h_ar", GalleryTab.Calligraphy, R.string.tpl_cal_month_h_ar, WidgetType.CalligraphyMonth, dark.copy(fontId = "arefruqaa", variant = 1), wide = true),
        t("cal_month_g_en", GalleryTab.Calligraphy, R.string.tpl_cal_month_g_en, WidgetType.CalligraphyMonth, dark.copy(fontId = "cinzel", variant = 2), wide = true),
        t("cal_month_h_en", GalleryTab.Calligraphy, R.string.tpl_cal_month_h_en, WidgetType.CalligraphyMonth, dark.copy(fontId = "cinzel", variant = 3), wide = true),
        t("cal_month_g_ar_sq", GalleryTab.Calligraphy, R.string.tpl_cal_month_g_ar, WidgetType.CalligraphyMonth, dark.copy(fontId = "arefruqaa"), wide = false),
        t("cal_month_h_ar_sq", GalleryTab.Calligraphy, R.string.tpl_cal_month_h_ar, WidgetType.CalligraphyMonth, dark.copy(fontId = "arefruqaa", variant = 1), wide = false),

        // Clocks
        t("analog_light", GalleryTab.Clock, R.string.tpl_analog_glass, WidgetType.AnalogClock, night.copy(cornerDp = 40), wide = false, isNew = true),
        t("analog_frost", GalleryTab.Clock, R.string.tpl_analog_frost, WidgetType.AnalogClock, frost.copy(variant = 1, cornerDp = 40), wide = false),
        t("analog_gold", GalleryTab.Clock, R.string.tpl_analog_gold, WidgetType.AnalogClock, gold.copy(cornerDp = 40), wide = false),
        // Digital
        t("digital_ruqaa", GalleryTab.Digital, R.string.tpl_digital_ruqaa, WidgetType.DigitalClock, night.copy(fontId = "arefruqaa"), wide = true, isNew = true),
        t("digital_amiri", GalleryTab.Digital, R.string.tpl_digital_amiri, WidgetType.DigitalClock, gold.copy(fontId = "amiri"), wide = true),
        t("digital_clear", GalleryTab.Digital, R.string.tpl_digital_clear, WidgetType.DigitalClock, WidgetPresets.invisible.copy(fontId = "lalezar"), wide = true),
        t("digital_small", GalleryTab.Digital, R.string.tpl_digital_small, WidgetType.DigitalClock, aurora.copy(fontId = "tajawal", variant = 1), wide = false),
        // Date
        t("date_words", GalleryTab.Date, R.string.tpl_date_words, WidgetType.Date, night.copy(fontId = "amiri"), wide = false, isNew = true),
        t("date_numeric", GalleryTab.Date, R.string.tpl_date_numeric, WidgetType.Date, frost.copy(dateStyle = DateStyle.Numeric), wide = false),
        t("date_wide", GalleryTab.Date, R.string.tpl_date_wide, WidgetType.Date, aurora.copy(fontId = "arefruqaa"), wide = true),
        // Prayer
        t("prayer_table", GalleryTab.Prayer, R.string.tpl_prayer_table, WidgetType.NextPrayer, night, wide = true, isNew = true),
        t("prayer_gold", GalleryTab.Prayer, R.string.tpl_prayer_gold, WidgetType.NextPrayer, gold.copy(fontId = "amiri"), wide = true),
        t("prayer_iqama", GalleryTab.Prayer, R.string.tpl_prayer_iqama, WidgetType.NextPrayer, night.copy(variant = PrayerPulse.IQAMA), wide = true, isNew = true),
        t("prayer_ring", GalleryTab.Prayer, R.string.tpl_prayer_ring, WidgetType.NextPrayer, night.copy(variant = PrayerPulse.RING), wide = false, isNew = true),
        t("prayer_bar", GalleryTab.Prayer, R.string.tpl_prayer_bar, WidgetType.NextPrayer, gold.copy(variant = PrayerPulse.BAR), wide = true, isNew = true),
        t("prayer_small", GalleryTab.Prayer, R.string.tpl_prayer_small, WidgetType.NextPrayer, clear, wide = false),
        t("tasbih", GalleryTab.Prayer, R.string.widget_type_tasbih, WidgetType.Tasbih, aurora, wide = false),
        // Counters
        t("counter_glass", GalleryTab.Counters, R.string.tpl_counter, WidgetType.Counter, night, wide = false),
        t("counter_gold", GalleryTab.Counters, R.string.tpl_counter_gold, WidgetType.Counter, gold.copy(fontId = "lalezar"), wide = false),
        t("tasks", GalleryTab.Counters, R.string.widget_type_tasks, WidgetType.TodayTasks, night, wide = true),
        // Habits
        t("habits_row", GalleryTab.Habits, R.string.widget_type_habits, WidgetType.Habits, aurora, wide = true),
        t("habit_one", GalleryTab.Habits, R.string.widget_type_habit, WidgetType.Habit, night, wide = false),
        // Debts
        t("debts_summary", GalleryTab.Debts, R.string.widget_type_debts, WidgetType.Debts, night, wide = true),
        t("debt_person", GalleryTab.Debts, R.string.widget_type_debt_person, WidgetType.DebtPerson, frost, wide = false),
        // Salaries
        t("schedule", GalleryTab.Salaries, R.string.widget_type_schedule, WidgetType.Schedule, night, wide = true, isNew = true),
        t("schedule_gold", GalleryTab.Salaries, R.string.tpl_schedule_gold, WidgetType.Schedule, gold, wide = true),
        t("salary_next", GalleryTab.Salaries, R.string.widget_type_salary, WidgetType.Salary, aurora, wide = false),
        // Quotes
        t("verse", GalleryTab.Quotes, R.string.widget_type_verse, WidgetType.Verse, night.copy(fontId = "amiri"), wide = true, isNew = true),
        t("dhikr", GalleryTab.Quotes, R.string.widget_type_dhikr, WidgetType.Dhikr, gold.copy(fontId = "scheherazade"), wide = true),
        t("verse_ruqaa", GalleryTab.Quotes, R.string.tpl_verse_ruqaa, WidgetType.Verse, frost.copy(fontId = "arefruqaa"), wide = false),
    )

    fun forTab(tab: GalleryTab) = if (tab == GalleryTab.New) all.filter { it.isNew } else all.filter { it.tab == tab }
}

/**
 * Widget gallery: tabs, a grid of live previews, and a customization screen
 * before adding to the home screen.
 */
@Composable
fun WidgetGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as AtharApplication).container.settings
    val saved by settings.savedDesigns.collectAsState()
    var tab by rememberSaveable { mutableStateOf(if (settings.savedDesigns.value.isEmpty()) GalleryTab.Nothing else GalleryTab.Mine) }
    var customizing by remember { mutableStateOf<WidgetTemplate?>(null) }
    val pinRequested = stringResource(R.string.pin_requested)
    val pinUnsupported = stringResource(R.string.pin_unsupported)
    val needItem = stringResource(R.string.gallery_need_item)
    val hidden by settings.hiddenWidgets.collectAsState()
    val savedMsg = stringResource(R.string.design_saved)
    val hiddenMsg = stringResource(R.string.design_hidden)
    val deletedMsg = stringResource(R.string.design_deleted)
    val newDesignName = stringResource(R.string.design_new)
    fun remove(t: WidgetTemplate) {
        if (t.savedId != null) {
            settings.deleteDesign(t.savedId)
            Toast.makeText(context, deletedMsg, Toast.LENGTH_SHORT).show()
        } else {
            settings.setWidgetHidden(t.id, true)
            Toast.makeText(context, hiddenMsg, Toast.LENGTH_LONG).show()
        }
    }

    // First record of each kind, so item-based templates show real data.
    val firstItems by produceState(emptyMap<WidgetType, Long>()) {
        val c = (context.applicationContext as AtharApplication).container
        value = mapOf(
            WidgetType.Counter to (c.counterRepository.counters.first().firstOrNull()?.id ?: -1),
            WidgetType.Habit to (c.habitRepository.habits.first().firstOrNull()?.id ?: -1),
            WidgetType.DebtPerson to (c.debtRepository.people.first().firstOrNull()?.id ?: -1),
            WidgetType.NothingCounter to (c.counterRepository.counters.first().firstOrNull()?.id ?: -1),
        )
    }
    fun resolved(t: WidgetTemplate): WidgetConfig {
        val type = t.config.content.type
        return if (type.needsItem) t.config.copy(content = t.config.content.copy(itemId = firstItems[type] ?: -1)) else t.config
    }

    customizing?.let { template ->
        BackHandler { customizing = null }
        WidgetEditorScreen(
            initial = resolved(template),
            wide = template.wide,
            saveLabel = stringResource(R.string.add_to_home_screen),
            onCancel = { customizing = null },
            onSaveAsDesign = { config, name ->
                settings.saveDesign(name, config, template.wide)
                Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                tab = GalleryTab.Mine
                customizing = null
            },
            onSave = { config ->
                val ok = WidgetPinner.pin(context, config, small = !template.wide)
                Toast.makeText(context, if (ok) pinRequested else pinUnsupported, Toast.LENGTH_LONG).show()
                if (ok) customizing = null
            },
        )
        return
    }

    BackHandler(onBack = onBack)
    HideBottomBar()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().padding(top = top + 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.gallery_subtitle), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(GalleryTab.entries) { t ->
                val selected = t == tab
                Text(
                    stringResource(t.label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) AtharColors.Night else AtharColors.TextSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) AtharColors.Gold else Color.White.copy(alpha = 0.08f))
                        .clickable { tab = t }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val visible = if (tab == GalleryTab.Mine) {
                saved.map {
                    WidgetTemplate("saved:" + it.id, GalleryTab.Mine, 0, it.config, it.wide, title = it.name, savedId = it.id)
                }
            } else {
                WidgetTemplates.forTab(tab).filterNot { it.id in hidden }
            }
            if (tab == GalleryTab.Mine) {
                item(key = "new", span = { GridItemSpan(2) }) {
                    NewDesignCell {
                        customizing = WidgetTemplate(
                            "new", GalleryTab.Mine, 0,
                            WidgetConfig(
                                WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(CustomSpec.starter())),
                                WidgetPresets.glow,
                            ),
                            wide = true,
                            title = newDesignName,
                        )
                    }
                }
            }
            if (visible.isEmpty() && tab != GalleryTab.Mine) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        stringResource(R.string.gallery_tab_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtharColors.TextTertiary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(visible, key = { it.id }, span = { GridItemSpan(if (it.wide) 2 else 1) }) { template ->
                TemplateCell(template, resolved(template), onRemove = { remove(template) }) {
                    val type = template.config.content.type
                    if (type.needsItem && (firstItems[type] ?: -1) < 0) {
                        Toast.makeText(context, needItem, Toast.LENGTH_LONG).show()
                    } else {
                        customizing = template
                    }
                }
            }
        }
    }
}

/** "New design": start from elements (time, date, days, prayer…). */
@Composable
private fun NewDesignCell(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, AtharColors.Violet.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(AtharColors.Violet.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = AtharColors.Violet)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.design_new), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.design_new_body), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
        }
    }
}

@Composable
private fun TemplateCell(template: WidgetTemplate, config: WidgetConfig, onRemove: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<WidgetData>(WidgetData.Unconfigured, config) {
        value = WidgetDataLoader.load(context, config)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(config.style.cornerDp.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(config.style.cornerDp.dp)),
        ) {
            WidgetPreview(config, data, template.wide, onTap = onClick)
            // Remove from the list (saved designs are deleted, ready designs hidden).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(if (template.savedId != null) R.string.delete else R.string.design_hide),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(template.title ?: stringResource(template.name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (template.isNew) {
                Text(
                    stringResource(R.string.gallery_new),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtharColors.Night,
                    modifier = Modifier.clip(CircleShape).background(AtharColors.Gold).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
