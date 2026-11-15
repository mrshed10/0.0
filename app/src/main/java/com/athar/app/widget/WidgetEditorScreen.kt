package com.athar.app.widget

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.HideBottomBar
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.format.currentLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Palette = listOf(
    0xFF0B1026, 0xFF000000, 0xFF1F2937, 0xFF3A3F9E, 0xFF8B7CF6, 0xFF2EC4B6,
    0xFF0E5E6F, 0xFFE6C27A, 0xFFF28AB2, 0xFFB91C1C, 0xFF166534, 0xFFFFFFFF,
).map { it.toInt() }

private val TextPalette = listOf(0xFFFFFFFF, 0xFF0B1026, 0xFFE6C27A, 0xFF2EC4B6, 0xFFF28AB2, 0xFF8B7CF6).map { it.toInt() }

/** A record a widget can point at. */
private data class Pickable(val id: Long, val title: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorScreen(
    initial: WidgetConfig,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
    wide: Boolean = true,
    saveLabel: String? = null,
    /** The placed widget's real size, so the preview matches it exactly. */
    previewSize: androidx.compose.ui.unit.DpSize? = null,
    /** Shows a name field (saved designs). */
    name: androidx.compose.runtime.MutableState<String>? = null,
    /** Optional second action, e.g. "Save to My designs". */
    secondaryLabel: String? = null,
    onSecondary: ((WidgetConfig) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(initial) }
    val style = config.style
    fun setStyle(s: WidgetStyle) { config = config.copy(style = s) }
    BackHandler(onBack = onCancel)
    HideBottomBar()

    // Items for types that point at one record.
    val items by produceState(emptyList<Pickable>(), config.content.type) {
        val c = (context.applicationContext as AtharApplication).container
        value = when (config.content.type) {
            WidgetType.Counter -> c.counterRepository.counters.first().map { Pickable(it.id, it.title) }
            WidgetType.Habit -> c.habitRepository.habits.first().map { Pickable(it.id, it.title) }
            WidgetType.DebtPerson -> c.debtRepository.people.first().map { Pickable(it.id, it.name) }
            WidgetType.Salary -> c.plannerRepository.salaries.first().map { Pickable(it.id, it.name) }
            else -> emptyList()
        }
    }
    val data by produceState<WidgetData>(
        WidgetData.Unconfigured,
        config.content,
        config.style.dateStyle,
        config.style.variant,
        config.style.arabicDigits,
    ) {
        value = WidgetDataLoader.load(context, config)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { WidgetBackground.importImage(context, uri) }
                if (path != null) setStyle(style.copy(kind = BackgroundKind.Image, imagePath = path))
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 56.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onCancel)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.widget_editor_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        name?.let { nameState ->
            item {
                GlassTextField(
                    nameState.value,
                    { nameState.value = it.take(60) },
                    stringResource(R.string.design_name_hint),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WidgetPreview(config, data, wide, Modifier.fillMaxWidth(if (wide || previewSize != null) 1f else 0.55f), size = previewSize)
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
                CardHeader(stringResource(R.string.click_behavior), Icons.Rounded.TouchApp)
                Spacer(Modifier.height(10.dp))
                GlassSegmented(
                    options = listOf(
                        stringResource(R.string.click_none),
                        stringResource(R.string.click_quick),
                        stringResource(R.string.click_open),
                    ),
                    selectedIndex = style.click.ordinal,
                    onSelect = { setStyle(style.copy(click = ClickBehavior.entries[it])) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        when (style.click) {
                            ClickBehavior.None -> R.string.click_none_hint
                            ClickBehavior.Quick -> R.string.click_quick_hint
                            ClickBehavior.OpenApp -> R.string.click_open_hint
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtharColors.TextTertiary,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_content), Icons.Rounded.Check)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetType.entries.forEach { type ->
                        GlassChip(
                            "${type.emoji} " + stringResource(type.label),
                            if (type == config.content.type) AtharColors.Gold else AtharColors.TextSecondary,
                            Modifier.clickable {
                                config = config.copy(
                                    content = WidgetContent(
                                        type,
                                        extra = if (type == WidgetType.Custom) CustomSpecJson.encode(CustomSpec.starter()) else "",
                                    ),
                                )
                            },
                        )
                    }
                }
                val variants = variantLabels(config.content.type)
                if (variants.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.clock_design))
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        variants.forEachIndexed { i, label ->
                            GlassChip(
                                stringResource(label),
                                if (style.variant == i) AtharColors.Gold else AtharColors.TextSecondary,
                                Modifier.clickable { setStyle(style.copy(variant = i)) },
                            )
                        }
                    }
                }
                if (config.content.type == WidgetType.DigitalClock && !ClockLayouts.supportsFont(style.fontId)) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.clock_font_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.Rose)
                }
                if (config.content.type == WidgetType.BigClock) {
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.digits_label))
                    Spacer(Modifier.height(6.dp))
                    GlassSegmented(
                        options = listOf("12:34", "١٢:٣٤"),
                        selectedIndex = if (style.arabicDigits) 1 else 0,
                        onSelect = { setStyle(style.copy(arabicDigits = it == 1)) },
                    )
                }
                if (config.content.type == WidgetType.WorldClock) {
                    val (a, b) = WorldCities.pair(config.content.extra)
                    val arabic = currentLocale().language == "ar"
                    listOf(R.string.world_city_1 to a, R.string.world_city_2 to b).forEachIndexed { slot, (label, chosen) ->
                        Spacer(Modifier.height(12.dp))
                        FieldLabel(stringResource(label))
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WorldCities.all.forEach { city ->
                                GlassChip(
                                    city.name(arabic),
                                    if (city.id == chosen.id) AtharColors.Gold else AtharColors.TextSecondary,
                                    Modifier.clickable {
                                        val ids = if (slot == 0) "${city.id},${b.id}" else "${a.id},${city.id}"
                                        config = config.copy(content = config.content.copy(extra = ids))
                                    },
                                )
                            }
                        }
                    }
                }
                if (config.content.type.needsItem || config.content.type == WidgetType.Salary) {
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.widget_choose_item))
                    Spacer(Modifier.height(6.dp))
                    if (items.isEmpty()) {
                        Text(stringResource(R.string.widget_no_items), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextTertiary)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (config.content.type == WidgetType.Salary) {
                            GlassChip(
                                stringResource(R.string.widget_nearest),
                                if (config.content.itemId < 0) AtharColors.Gold else AtharColors.TextSecondary,
                                Modifier.clickable { config = config.copy(content = config.content.copy(itemId = -1)) },
                            )
                        }
                        items.forEach { item ->
                            GlassChip(
                                item.title,
                                if (item.id == config.content.itemId) AtharColors.Gold else AtharColors.TextSecondary,
                                Modifier.clickable { config = config.copy(content = config.content.copy(itemId = item.id)) },
                            )
                        }
                    }
                }
            }
        }

        if (config.content.type == WidgetType.Custom) {
            item {
                CustomDesigner(
                    spec = CustomSpecJson.decode(config.content.extra),
                    onChange = { config = config.copy(content = config.content.copy(extra = CustomSpecJson.encode(it))) },
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_presets), Icons.Rounded.Check, tint = AtharColors.Violet)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetPresets.all.forEach { preset ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(preset.color1).copy(alpha = (preset.opacity / 100f).coerceAtLeast(0.15f)),
                                            Color(if (preset.kind == BackgroundKind.Gradient) preset.color2 else preset.color1)
                                                .copy(alpha = (preset.opacity / 100f).coerceAtLeast(0.15f)),
                                        ),
                                    ),
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { setStyle(preset.copy(imagePath = style.imagePath, fontId = style.fontId)) },
                        )
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_background), Icons.Rounded.Image, tint = AtharColors.Teal)
                Spacer(Modifier.height(10.dp))
                GlassSegmented(
                    options = listOf(
                        stringResource(R.string.bg_color),
                        stringResource(R.string.bg_gradient),
                        stringResource(R.string.bg_image),
                        stringResource(R.string.bg_dots),
                    ),
                    selectedIndex = style.kind.ordinal,
                    onSelect = {
                        val kind = BackgroundKind.entries[it]
                        if (kind == BackgroundKind.Image && style.imagePath == null) {
                            pickImage.launch(arrayOf("image/*"))
                        } else {
                            setStyle(style.copy(kind = kind))
                        }
                    },
                    accent = AtharColors.Teal,
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(if (style.kind == BackgroundKind.Image) R.string.bg_veil_color else R.string.bg_color_1))
                Spacer(Modifier.height(6.dp))
                Swatches(Palette, style.color1) { setStyle(style.copy(color1 = it)) }
                if (style.kind == BackgroundKind.Gradient) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel(stringResource(R.string.bg_color_2))
                    Spacer(Modifier.height(6.dp))
                    Swatches(Palette, style.color2) { setStyle(style.copy(color2 = it)) }
                    Spacer(Modifier.height(10.dp))
                    FieldLabel(stringResource(R.string.bg_direction))
                    Spacer(Modifier.height(6.dp))
                    val angles = listOf(0, 45, 90, 135)
                    GlassSegmented(
                        options = listOf("→", "↘", "↓", "↙"),
                        selectedIndex = angles.indexOf(style.angle).coerceAtLeast(0),
                        onSelect = { setStyle(style.copy(angle = angles[it])) },
                        accent = AtharColors.Teal,
                    )
                }
                if (style.kind == BackgroundKind.Image) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        stringResource(R.string.bg_pick_image),
                        { pickImage.launch(arrayOf("image/*")) },
                        color = AtharColors.Teal,
                        filled = false,
                        icon = Icons.Rounded.Image,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LabeledSlider(stringResource(R.string.bg_blur), style.blur, 0..25) { setStyle(style.copy(blur = it)) }
                    LabeledSlider(stringResource(R.string.bg_veil), style.dim, 0..100, suffix = "%") { setStyle(style.copy(dim = it)) }
                }
                LabeledSlider(stringResource(R.string.bg_opacity), style.opacity, 0..100, suffix = "%") { setStyle(style.copy(opacity = it)) }
                LabeledSlider(stringResource(R.string.bg_corners), style.cornerDp, 0..40) { setStyle(style.copy(cornerDp = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bg_border), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = style.border,
                        onCheckedChange = { setStyle(style.copy(border = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Teal),
                    )
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_text_colors), Icons.Rounded.Check, tint = AtharColors.Rose)
                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.text_color))
                Spacer(Modifier.height(6.dp))
                Swatches(TextPalette, style.textColor) { setStyle(style.copy(textColor = it)) }
                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.accent_color))
                Spacer(Modifier.height(6.dp))
                Swatches(TextPalette, style.accentColor) { setStyle(style.copy(accentColor = it)) }
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.widget_date_style))
                Spacer(Modifier.height(6.dp))
                GlassSegmented(
                    options = listOf(stringResource(R.string.date_words), stringResource(R.string.date_numeric)),
                    selectedIndex = style.dateStyle.ordinal,
                    onSelect = { setStyle(style.copy(dateStyle = DateStyle.entries[it])) },
                    accent = AtharColors.Rose,
                )
            }
        }

        item {
            val fonts = remember { FontRegistry.all(context) }
            val locale = currentLocale()
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_font), Icons.Rounded.TextFields, tint = AtharColors.Gold)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChip(stringResource(R.string.font_system), FontFamily.Default, style.fontId == null) {
                        setStyle(style.copy(fontId = null))
                    }
                    fonts.forEach { info ->
                        val family = remember(info.id) { FontRegistry.composeFamily(context, info.id) }
                        FontChip(info.name(locale), family, style.fontId == info.id) { setStyle(style.copy(fontId = info.id)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.widget_font_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
        }

        if (secondaryLabel != null && onSecondary != null) {
            item {
                GlassButton(
                    secondaryLabel,
                    { onSecondary(config) },
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            GlassButton(
                saveLabel ?: stringResource(R.string.save),
                { onSave(config) },
                enabled = !config.content.type.needsItem || config.content.itemId >= 0,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Design options shown for a widget type (index = [WidgetStyle.variant]). */
private fun variantLabels(type: WidgetType): List<Int> = when (type) {
    WidgetType.AnalogClock, WidgetType.WideClock -> listOf(R.string.clock_light_hands, R.string.clock_dark_hands)
    WidgetType.DigitalClock -> listOf(R.string.clock_with_date, R.string.clock_time_only)
    WidgetType.WorldClock -> listOf(R.string.variant_side_by_side, R.string.variant_stacked)
    WidgetType.BigClock -> listOf(R.string.variant_horizontal, R.string.variant_vertical)
    WidgetType.YearDots -> listOf(R.string.variant_year, R.string.variant_month)
    WidgetType.MonthCalendar -> listOf(R.string.variant_week_sunday, R.string.variant_week_saturday)
    WidgetType.CalligraphyDay -> listOf(R.string.variant_arabic, R.string.variant_english, R.string.variant_english_short)
    WidgetType.CalligraphyMonth -> listOf(
        R.string.variant_gregorian_ar, R.string.variant_hijri_ar, R.string.variant_gregorian_en, R.string.variant_hijri_en,
    )
    WidgetType.Date -> listOf(
        R.string.variant_classic, R.string.variant_simple, R.string.variant_strip, R.string.variant_hijri_greg, R.string.variant_dual,
    )
    else -> emptyList()
}

@Composable
private fun FontChip(name: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        fontFamily = family,
        fontSize = 16.sp,
        color = if (selected) AtharColors.Gold else AtharColors.TextSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AtharColors.Gold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { argb ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(2.dp, if (argb == selected) AtharColors.Gold else Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable { onPick(argb) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$value$suffix", style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = AtharColors.Gold,
                activeTrackColor = AtharColors.Gold,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}

