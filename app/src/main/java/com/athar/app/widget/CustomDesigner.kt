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
import androidx.compose.ui.text.font.FontWeight
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
                        (if (e.kind == ElementKind.Text) e.text else "") + " · ${e.sizeSp}sp" + (if (e.dots) " · ⠿" else ""),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementEditor(e: CustomElement, onChange: (CustomElement) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.designer_element), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        Stepper(
            stringResource(R.string.designer_size),
            "${e.sizeSp}sp",
            { n -> onChange(e.copy(sizeSp = (e.sizeSp + n.toInt()).coerceIn(8, 96))) },
            Modifier.fillMaxWidth(),
        )
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
            ElementColors.forEach { argb ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(2.dp, if (e.color == argb) AtharColors.Gold else Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onChange(e.copy(color = argb, accent = false)) },
                )
            }
        }
        ToggleRow(stringResource(R.string.designer_bold), e.bold) { onChange(e.copy(bold = it)) }
        ToggleRow(stringResource(R.string.designer_dots), e.dots) { onChange(e.copy(dots = it)) }
        if (e.dots) {
            Text(stringResource(R.string.designer_dots_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        }
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
