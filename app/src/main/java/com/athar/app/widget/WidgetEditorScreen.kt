package com.athar.app.widget

import androidx.activity.compose.BackHandler
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

private val TextPalette = listOf(0xFFFFFFFF, 0xFF111111, 0xFFD71921, 0xFFE6C27A, 0xFF2EC4B6, 0xFF8B7CF6).map { it.toInt() }

/** A record a widget can point at. */
private data class Pickable(val id: Long, val title: String)

/**
 * Simple customization of a ready-made design: opacity, colours and tap
 * behaviour (plus the item for widgets that show one record).
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

        // Opacity
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.bg_opacity), Icons.Rounded.Opacity, tint = AtharColors.Teal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = style.opacity.toFloat(),
                        onValueChange = { setStyle(style.copy(opacity = it.toInt())) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = AtharColors.Gold,
                            activeTrackColor = AtharColors.Gold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("${style.opacity}%", style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
                }
            }
        }

        // Colours
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(stringResource(R.string.designer_color), Icons.Rounded.Palette, tint = AtharColors.Rose)
                Spacer(Modifier.height(10.dp))
                FieldLabel(stringResource(R.string.widget_background))
                Spacer(Modifier.height(6.dp))
                Swatches(BackgroundPalette, style.color1) { setStyle(style.copy(color1 = it, color2 = it)) }
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.text_color))
                Spacer(Modifier.height(6.dp))
                Swatches(TextPalette, style.textColor) { setStyle(style.copy(textColor = it)) }
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.accent_color))
                Spacer(Modifier.height(6.dp))
                Swatches(TextPalette, style.accentColor) { setStyle(style.copy(accentColor = it)) }
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
