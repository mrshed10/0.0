package com.athar.app.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.athar.app.core.designsystem.component.ColorChoices
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.fonts.FontScript
import com.athar.app.core.format.currentLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.ui.Stepper
import kotlinx.coroutines.flow.first

private val ElementColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE6C27A, 0xFFD71921, 0xFF2EC4B6, 0xFF8B7CF6, 0xFFF28AB2, 0xFF9CA3AF,
).map { it.toInt() }

/** Build your own widget from stacked elements. */
@Composable
fun CustomDesigner(spec: CustomSpec, onChange: (CustomSpec) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
        CardHeader(stringResource(R.string.designer_title), Icons.Rounded.Palette, tint = AtharColors.Violet)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.designer_tap_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        Spacer(Modifier.height(10.dp))
        FieldLabel(stringResource(R.string.designer_align))
        Spacer(Modifier.height(6.dp))
        GlassSegmented(
            options = listOf(stringResource(R.string.align_start), stringResource(R.string.align_center), stringResource(R.string.align_end)),
            selectedIndex = spec.align,
            onSelect = { onChange(spec.copy(align = it)) },
            accent = AtharColors.Violet,
        )
        Spacer(Modifier.height(10.dp))
        spec.elements.forEachIndexed { i, e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { editing = i }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = AtharColors.Violet)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(e.kind.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            e.text.takeIf { e.kind == ElementKind.Text && it.isNotBlank() },
                            "${e.sizeSp}sp",
                            e.fontId?.let { id -> FontRegistry.bundled.firstOrNull { it.id == id }?.nameEn ?: id },
                            "B".takeIf { e.bold }, "I".takeIf { e.italic }, "U".takeIf { e.underline }, "⠿".takeIf { e.dots },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = AtharColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onChange(spec.copy(elements = spec.elements.swap(i, i - 1))) }, enabled = i > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up), tint = AtharColors.TextSecondary)
                }
                IconButton(onClick = { onChange(spec.copy(elements = spec.elements.swap(i, i + 1))) }, enabled = i < spec.elements.lastIndex) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down), tint = AtharColors.TextSecondary)
                }
                IconButton(onClick = { onChange(spec.copy(elements = spec.elements.filterIndexed { j, _ -> j != i })) }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = AtharColors.Rose)
                }
            }
        }
        if (spec.elements.size < CustomSpecJson.MAX_ELEMENTS) {
            Spacer(Modifier.height(8.dp))
            val defaultText = stringResource(R.string.app_name)
            GlassButton(
                stringResource(R.string.designer_add),
                {
                    onChange(spec.copy(elements = spec.elements + CustomElement(ElementKind.Text, text = defaultText)))
                    editing = spec.elements.size
                },
                color = AtharColors.Violet,
                filled = false,
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val index = editing
    val element = index?.let { spec.elements.getOrNull(it) }
    if (index != null && element != null) {
        GlassSheet(onDismiss = { editing = null }) {
            ElementEditor(element) { updated ->
                onChange(spec.copy(elements = spec.elements.toMutableList().also { it[index] = updated }))
            }
            GlassButton(stringResource(R.string.done), { editing = null }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun <T> List<T>.swap(i: Int, j: Int): List<T> {
    if (i !in indices || j !in indices) return this
    return toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
}

/**
 * Settings of ONE element: its content, font, size, colour, opacity, style and
 * alignment. Nothing here changes the other elements of the widget.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementEditor(e: CustomElement, onChange: (CustomElement) -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val fonts = remember { FontRegistry.all(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.designer_element), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Live sample of this element only.
        val sampleFamily = remember(e.fontId) { e.fontId?.let { FontRegistry.composeFamily(context, it) } ?: FontFamily.Default }
        val sampleColor = (e.color?.let { Color(it) } ?: if (e.accent) AtharColors.Gold else Color.White)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(14.dp),
            contentAlignment = when (e.align) {
                0 -> Alignment.CenterStart
                2 -> Alignment.CenterEnd
                else -> Alignment.Center
            },
        ) {
            Text(
                if (e.kind == ElementKind.Text) e.text.ifBlank { "…" } else stringResource(e.kind.label),
                fontFamily = sampleFamily,
                fontSize = e.sizeSp.coerceAtMost(40).sp,
                fontWeight = if (e.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (e.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (e.underline) TextDecoration.Underline else null,
                color = sampleColor.copy(alpha = e.opacity / 100f),
                maxLines = 2,
            )
        }

        FieldLabel(stringResource(R.string.designer_kind))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ElementKind.entries.forEach { kind ->
                GlassChip(
                    stringResource(kind.label),
                    if (kind == e.kind) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { onChange(e.copy(kind = kind)) },
                )
            }
        }
        if (e.kind == ElementKind.Text) {
            GlassTextField(e.text, { onChange(e.copy(text = it.take(80))) }, stringResource(R.string.designer_text_hint), Modifier.fillMaxWidth())
        }
        if (e.kind == ElementKind.Counter) {
            val counters by produceState(emptyList<Pair<Long, String>>()) {
                value = (context.applicationContext as AtharApplication).container.counterRepository.counters.first().map { it.id to it.title }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip(
                    stringResource(R.string.widget_nearest),
                    if (e.ref < 0) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { onChange(e.copy(ref = -1)) },
                )
                counters.forEach { (id, title) ->
                    GlassChip(title, if (id == e.ref) AtharColors.Gold else AtharColors.TextSecondary, Modifier.clickable { onChange(e.copy(ref = id)) })
                }
            }
        }

        // Font of this element only.
        FieldLabel(stringResource(R.string.element_font))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontPill(stringResource(R.string.element_font_widget), FontFamily.Default, e.fontId == null) { onChange(e.copy(fontId = null)) }
            fonts.forEach { info ->
                val family = remember(info.id) { FontRegistry.composeFamily(context, info.id) }
                FontPill(
                    if (info.script == FontScript.Arabic) info.name(locale) else info.nameEn,
                    family,
                    e.fontId == info.id,
                ) { onChange(e.copy(fontId = info.id)) }
            }
        }

        // Size: quick stepper + slider.
        Stepper(
            stringResource(R.string.designer_size),
            "${e.sizeSp}sp",
            { n -> onChange(e.copy(sizeSp = (e.sizeSp + n.toInt()).coerceIn(8, 96))) },
            Modifier.fillMaxWidth(),
        )
        Slider(
            value = e.sizeSp.toFloat(),
            onValueChange = { onChange(e.copy(sizeSp = it.toInt().coerceIn(8, 96))) },
            valueRange = 8f..96f,
            colors = SliderDefaults.colors(thumbColor = AtharColors.Violet, activeTrackColor = AtharColors.Violet, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
        )

        // Colour of this element only.
        FieldLabel(stringResource(R.string.designer_color))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(
                stringResource(R.string.designer_color_auto),
                if (e.color == null && !e.accent) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { onChange(e.copy(color = null, accent = false)) },
            )
            GlassChip(
                stringResource(R.string.accent_color),
                if (e.color == null && e.accent) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { onChange(e.copy(color = null, accent = true)) },
            )
        }
        ColorChoices(ElementColors, e.color, { onChange(e.copy(color = it, accent = false)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.element_opacity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${e.opacity}%", style = MaterialTheme.typography.labelLarge, color = AtharColors.Violet)
        }
        Slider(
            value = e.opacity.toFloat(),
            onValueChange = { onChange(e.copy(opacity = it.toInt().coerceIn(10, 100))) },
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(thumbColor = AtharColors.Violet, activeTrackColor = AtharColors.Violet, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
        )

        // Formatting.
        FieldLabel(stringResource(R.string.element_format))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatChip("B", stringResource(R.string.designer_bold), e.bold, FontWeight.Bold) { onChange(e.copy(bold = !e.bold)) }
            FormatChip("I", stringResource(R.string.element_italic), e.italic, style = FontStyle.Italic) { onChange(e.copy(italic = !e.italic)) }
            FormatChip("U", stringResource(R.string.element_underline), e.underline, underline = true) { onChange(e.copy(underline = !e.underline)) }
            FormatChip("⠿", stringResource(R.string.designer_dots), e.dots) { onChange(e.copy(dots = !e.dots)) }
        }
        if (e.dots) {
            Text(stringResource(R.string.designer_dots_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        }
        FieldLabel(stringResource(R.string.designer_align))
        GlassSegmented(
            options = listOf(
                stringResource(R.string.element_align_widget),
                stringResource(R.string.align_start),
                stringResource(R.string.align_center),
                stringResource(R.string.align_end),
            ),
            selectedIndex = (e.align ?: -1) + 1,
            onSelect = { onChange(e.copy(align = if (it == 0) null else it - 1)) },
            accent = AtharColors.Violet,
        )
    }
}

@Composable
private fun FontPill(name: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        fontFamily = family,
        fontSize = 15.sp,
        color = if (selected) AtharColors.Gold else AtharColors.TextSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AtharColors.Gold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun FormatChip(
    symbol: String,
    label: String,
    on: Boolean,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    underline: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) AtharColors.Violet.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (on) AtharColors.Violet else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            symbol,
            fontWeight = if (weight == FontWeight.Normal) FontWeight.Bold else weight,
            fontStyle = style,
            textDecoration = if (underline) TextDecoration.Underline else null,
            color = if (on) Color.White else AtharColors.TextSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else AtharColors.TextSecondary)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Violet),
        )
    }
}
