package com.athar.app.widget

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Grain
import com.athar.app.core.designsystem.component.ColorChoices
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ViewAgenda
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassSheet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.fonts.FontScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.HideBottomBar
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import kotlinx.coroutines.flow.first

private val BackgroundPalette = listOf(
    0xFF000000, 0xFF0B1026, 0xFF1C1C1E, 0xFF3A3F9E, 0xFF0E5E6F, 0xFF3B2A12,
    0xFFD71921, 0xFFE6C27A, 0xFF8B7CF6, 0xFF2EC4B6, 0xFFF1F1F1, 0xFFFFFFFF,
).map { it.toInt() }

private val TextPalette = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFD71921, 0xFFE6C27A, 0xFF2EC4B6, 0xFF8B7CF6, 0xFFF28AB2, 0xFF7FD1FF, 0xFFB8F2A0, 0xFFFFB86B,
).map { it.toInt() }

/** Lit dots: blue, white, green, red, gold, teal, violet, pink, orange. */
private val LitDotPalette = listOf(
    0xFF3D8BFF, 0xFFFFFFFF, 0xFF34D058, 0xFFD71921, 0xFFE6C27A, 0xFF2EC4B6, 0xFF8B7CF6, 0xFFF28AB2, 0xFFFF9F43,
).map { it.toInt() }

/** Unlit dots: faint and dark tones (transparency kept). */
private val UnlitDotPalette = listOf(
    0x2EFFFFFF, 0x55FFFFFF, 0x553D8BFF, 0x5534D058, 0x55D71921, 0xFF1B2A4A, 0xFF16321F, 0xFF2A2A2A, 0x00000000,
).map { it.toInt() }

/** True if the design draws dots whose colours can be chosen. */
private fun usesDots(config: WidgetConfig): Boolean {
    val type = config.content.type
    val style = config.style
    return style.kind == BackgroundKind.DotGrid || style.kind == BackgroundKind.GlowDots ||
        type == WidgetType.YearDots || type.name.startsWith("Nothing") ||
        (type == WidgetType.NextPrayer && PrayerPulse.isAnimatedVariant(style.variant))
}

/** Looks offered in the editor (label, style). */
private val EditorPresets: List<Pair<Int, WidgetStyle>> = listOf(
    R.string.preset_nothing_dark to WidgetTemplates.nothingDark,
    R.string.preset_nothing_light to WidgetTemplates.nothingLight,
    R.string.preset_glow to WidgetPresets.glow,
    R.string.preset_glass to WidgetPresets.glassNight,
    R.string.preset_clear to WidgetPresets.glassClear,
    R.string.preset_frost to WidgetPresets.frost,
    R.string.preset_gold to WidgetPresets.gold,
    R.string.preset_aurora to WidgetPresets.aurora,
    R.string.preset_invisible to WidgetPresets.invisible,
)

/** A record a widget can point at. */
private data class Pickable(val id: Long, val title: String)

/**
 * Full customization of a ready-made design: item, design variant, look,
 * background (incl. glowing dots and photo), colours, date format, font and
 * tap behaviour, with a live preview.
 */
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
    /** Saves the current look as a custom design in "My designs" (name chosen by the user). */
    onSaveAsDesign: ((WidgetConfig, String) -> Unit)? = null,
    /** The user's saved designs, offered as one-tap starting points. */
    designs: List<SavedDesign> = emptyList(),
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(initial) }
    val style = config.style
    fun setStyle(s: WidgetStyle) { config = config.copy(style = s) }
    BackHandler(onBack = onCancel)
    HideBottomBar()

    val type = config.content.type
    val items by produceState(emptyList<Pickable>(), type) {
        val c = (context.applicationContext as AtharApplication).container
        value = when (type) {
            WidgetType.Counter, WidgetType.NothingCounter -> c.counterRepository.counters.first().map { Pickable(it.id, it.title) }
            WidgetType.Habit -> c.habitRepository.habits.first().map { Pickable(it.id, it.title) }
            WidgetType.DebtPerson -> c.debtRepository.people.first().map { Pickable(it.id, it.name) }
            WidgetType.Salary -> c.plannerRepository.salaries.first().map { Pickable(it.id, it.name) }
            else -> emptyList()
        }
    }
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    if (naming && onSaveAsDesign != null) {
        val defaultName = stringResource(type.label)
        var name by remember { mutableStateOf(defaultName) }
        GlassSheet(onDismiss = { naming = false }) {
            Text(stringResource(R.string.save_as_design), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            GlassTextField(name, { name = it.take(40) }, stringResource(R.string.design_name_hint), Modifier.fillMaxWidth())
            GlassButton(
                stringResource(R.string.save),
                {
                    onSaveAsDesign(config, name.ifBlank { defaultName })
                    naming = false
                },
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { WidgetBackground.importImage(context, uri) }
                if (path != null) config = config.copy(style = config.style.copy(kind = BackgroundKind.Image, imagePath = path))
            }
        }
    }
    val data by produceState<WidgetData>(WidgetData.Unconfigured, config.content, style.variant, style.dateStyle, style.arabicDigits) {
        value = WidgetDataLoader.load(context, config)
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
                Column {
                    Text(stringResource(R.string.widget_editor_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${type.emoji}  " + stringResource(type.label), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                }
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WidgetPreview(config, data, wide, Modifier.fillMaxWidth(if (wide || previewSize != null) 1f else 0.55f), size = previewSize)
            }
        }

        // One tap: use one of "My designs" for this widget.
        if (designs.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
                    CardHeader(stringResource(R.string.gallery_mine), Icons.Rounded.BookmarkAdd, tint = AtharColors.Violet)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        designs.forEach { d ->
                            GlassChip(
                                "${d.config.content.type.emoji} ${d.name}",
                                if (config == d.config) AtharColors.Gold else AtharColors.TextSecondary,
                                Modifier.clickable { config = d.config },
                            )
                        }
                    }
                }
            }
        }

        // Which record (counter, habit, person, salary) or which cities.
        if (type.needsItem || type == WidgetType.Salary) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    FieldLabel(stringResource(R.string.widget_choose_item))
                    Spacer(Modifier.height(6.dp))
                    if (items.isEmpty()) {
                        Text(stringResource(R.string.widget_no_items), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextTertiary)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == WidgetType.Salary) {
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
        if (type == WidgetType.WorldClock) {
            item {
                val (a, b) = WorldCities.pair(config.content.extra)
                val arabic = currentLocale().language == "ar"
                GlassCard(Modifier.fillMaxWidth()) {
                    listOf(R.string.world_city_1 to a, R.string.world_city_2 to b).forEachIndexed { slot, (label, chosen) ->
                        if (slot == 1) Spacer(Modifier.height(12.dp))
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
            }
        }

        // Elements: build the widget from time, date, days, prayer, counters...
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
                CardHeader(stringResource(R.string.elements_title), Icons.Rounded.ViewAgenda, tint = AtharColors.Violet)
                Spacer(Modifier.height(6.dp))
                if (type == WidgetType.Custom) {
                    Text(stringResource(R.string.elements_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
                    if (initial.content.type != WidgetType.Custom) {
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            stringResource(R.string.elements_back_to_design),
                            { config = config.copy(content = initial.content) },
                            filled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Text(stringResource(R.string.elements_convert_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        stringResource(R.string.elements_convert),
                        {
                            config = config.copy(
                                content = WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(CustomSpec.starter())),
                            )
                        },
                        color = AtharColors.Violet,
                        filled = false,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (type == WidgetType.Custom) {
            item {
                CustomDesigner(
                    spec = CustomSpecJson.decode(config.content.extra),
                    onChange = { config = config.copy(content = config.content.copy(extra = CustomSpecJson.encode(it))) },
                )
            }
        }

        // Design variant (per type) and digits.
        val variants = variantLabels(type)
        if (variants.isNotEmpty() || type == WidgetType.BigClock) {
            item {
                GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
                    CardHeader(stringResource(R.string.clock_design), Icons.Rounded.AutoAwesome, tint = AtharColors.Violet)
                    if (variants.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
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
                    if (type == WidgetType.NextPrayer && style.variant != PrayerPulse.CLASSIC) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.prayer_animated_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
                    }
                    if (type == WidgetType.DigitalClock && !ClockLayouts.supportsFont(style.fontId)) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.clock_font_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.Rose)
                    }
                    if (type == WidgetType.BigClock) {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel(stringResource(R.string.digits_label))
                        Spacer(Modifier.height(6.dp))
                        GlassSegmented(
                            options = listOf("12:34", "١٢:٣٤"),
                            selectedIndex = if (style.arabicDigits) 1 else 0,
                            onSelect = { setStyle(style.copy(arabicDigits = it == 1)) },
                        )
                    }
                }
            }
        }

        // Ready-made looks.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_presets), Icons.Rounded.Style, tint = AtharColors.Violet)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorPresets.forEach { (label, preset) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(46.dp)
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
                                    .clickable {
                                        setStyle(
                                            preset.copy(
                                                imagePath = style.imagePath, fontId = style.fontId, variant = style.variant,
                                                dateStyle = style.dateStyle, click = style.click, arabicDigits = style.arabicDigits,
                                                dotOn = style.dotOn, dotOff = style.dotOff,
                                            ),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (preset.kind == BackgroundKind.DotGrid || preset.kind == BackgroundKind.GlowDots) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(preset.accentColor)))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextSecondary)
                        }
                    }
                }
            }
        }

        // Background: kind, colours, image, opacity, corners, rim.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_background), Icons.Rounded.Opacity, tint = AtharColors.Teal)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        BackgroundKind.Color to R.string.bg_color,
                        BackgroundKind.Gradient to R.string.bg_gradient,
                        BackgroundKind.DotGrid to R.string.bg_dots,
                        BackgroundKind.GlowDots to R.string.bg_glow_dots,
                        BackgroundKind.Image to R.string.bg_image,
                    ).forEach { (kind, label) ->
                        GlassChip(
                            stringResource(label),
                            if (style.kind == kind) AtharColors.Teal else AtharColors.TextSecondary,
                            Modifier.clickable {
                                if (kind == BackgroundKind.Image && style.imagePath == null) {
                                    pickImage.launch(arrayOf("image/*"))
                                } else {
                                    setStyle(style.copy(kind = kind))
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(if (style.kind == BackgroundKind.Image) R.string.bg_veil_color else R.string.bg_color_1))
                Spacer(Modifier.height(6.dp))
                ColorChoices(BackgroundPalette, style.color1, { picked ->
                    picked?.let { setStyle(if (style.kind == BackgroundKind.Gradient) style.copy(color1 = it) else style.copy(color1 = it, color2 = it)) }
                })
                if (style.kind == BackgroundKind.Gradient) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel(stringResource(R.string.bg_color_2))
                    Spacer(Modifier.height(6.dp))
                    ColorChoices(BackgroundPalette, style.color2, { c -> c?.let { setStyle(style.copy(color2 = it)) } })
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
                Spacer(Modifier.height(6.dp))
                // One tap: no background and no rim, so the widget melts into the wallpaper.
                GlassButton(
                    stringResource(R.string.bg_blend_wallpaper),
                    { setStyle(style.copy(kind = BackgroundKind.Color, opacity = 0, border = false)) },
                    color = AtharColors.Teal,
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.bg_blend_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
        }

        // Text colours and date format.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_text_colors), Icons.Rounded.Palette, tint = AtharColors.Rose)
                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.text_color))
                Spacer(Modifier.height(6.dp))
                ColorChoices(TextPalette, style.textColor, { c -> c?.let { setStyle(style.copy(textColor = it)) } })
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.accent_color))
                Spacer(Modifier.height(6.dp))
                ColorChoices(TextPalette, style.accentColor, { c -> c?.let { setStyle(style.copy(accentColor = it)) } })
            }
        }
        // Dot colours (dot-matrix designs, dot backgrounds, animated prayer dots).
        if (usesDots(config)) {
            item {
                GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
                    CardHeader(stringResource(R.string.dot_colors), Icons.Rounded.Grain, tint = AtharColors.Teal)
                    Spacer(Modifier.height(10.dp))
                    FieldLabel(stringResource(R.string.dot_lit))
                    Spacer(Modifier.height(6.dp))
                    ColorChoices(LitDotPalette, style.dotOn, { setStyle(style.copy(dotOn = it)) }, defaultLabel = stringResource(R.string.dot_default))
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.dot_unlit))
                    Spacer(Modifier.height(6.dp))
                    ColorChoices(
                        UnlitDotPalette,
                        style.dotOff,
                        { setStyle(style.copy(dotOff = it)) },
                        defaultLabel = stringResource(R.string.dot_default),
                        allowAlpha = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dot_colors_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_date_style), Icons.Rounded.CalendarMonth, tint = AtharColors.Gold)
                Spacer(Modifier.height(10.dp))
                GlassSegmented(
                    options = listOf(stringResource(R.string.date_words), stringResource(R.string.date_numeric)),
                    selectedIndex = style.dateStyle.ordinal,
                    onSelect = { setStyle(style.copy(dateStyle = DateStyle.entries[it])) },
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.date_style_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
        }

        // Font, Arabic and English.
        item {
            val fonts = remember { FontRegistry.all(context) }
            val locale = currentLocale()
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.widget_font), Icons.Rounded.TextFields, tint = AtharColors.Gold)
                Spacer(Modifier.height(10.dp))
                FontChip(stringResource(R.string.font_system), FontFamily.Default, style.fontId == null) { setStyle(style.copy(fontId = null)) }
                listOf(FontScript.Arabic to R.string.fonts_arabic, FontScript.Latin to R.string.fonts_english).forEach { (script, label) ->
                    val group = fonts.filter { it.script == script }
                    if (group.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel(stringResource(label))
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { info ->
                                val family = remember(info.id) { FontRegistry.composeFamily(context, info.id) }
                                FontChip(
                                    if (script == FontScript.Arabic) info.name(locale) else info.nameEn,
                                    family,
                                    style.fontId == info.id,
                                ) { setStyle(style.copy(fontId = info.id)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.widget_font_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
        }

        // Tap behaviour
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

        if (onSaveAsDesign != null) {
            item {
                GlassButton(
                    stringResource(R.string.save_as_design),
                    { naming = true },
                    color = AtharColors.Violet,
                    filled = false,
                    icon = Icons.Rounded.BookmarkAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            GlassButton(
                saveLabel ?: stringResource(R.string.save),
                { onSave(config) },
                enabled = !type.needsItem || config.content.itemId >= 0,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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

/** Design options shown for a widget type (index = [WidgetStyle.variant]). */
private fun variantLabels(type: WidgetType): List<Int> = when (type) {
    WidgetType.NextPrayer -> listOf(
        R.string.variant_prayer_classic, R.string.variant_prayer_ring, R.string.variant_prayer_bar, R.string.variant_prayer_iqama,
    )
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
    WidgetType.NothingClock -> listOf(R.string.variant_gregorian_date, R.string.variant_hijri_date)
    WidgetType.NothingGlyph -> listOf(R.string.variant_day_progress, R.string.variant_year)
    WidgetType.NothingDate -> listOf(R.string.variant_gregorian_date, R.string.variant_hijri_date)
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
