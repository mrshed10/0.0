package com.athar.app.core.designsystem.component

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors

/**
 * A row of colour swatches with an optional "default" chip and a "custom"
 * swatch that opens a full colour picker (hue, saturation, brightness, opacity).
 * [selected] null = the default colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(
    colors: List<Int>,
    selected: Int?,
    onPick: (Int?) -> Unit,
    defaultLabel: String? = null,
    allowAlpha: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (defaultLabel != null) {
            GlassChip(
                defaultLabel,
                if (selected == null) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { onPick(null) },
            )
        }
        colors.forEach { argb ->
            Swatch(Color(argb), selected == argb) { onPick(argb) }
        }
        // Custom colour: shows the current custom choice, opens the picker.
        val custom = selected?.takeIf { it !in colors }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (custom != null) {
                        Brush.linearGradient(listOf(Color(custom), Color(custom)))
                    } else {
                        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    },
                )
                .border(2.dp, if (custom != null) AtharColors.Gold else Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Colorize, contentDescription = stringResource(R.string.color_custom), tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
    if (picking) {
        ColorPickerSheet(
            initial = selected ?: colors.firstOrNull() ?: 0xFFFFFFFF.toInt(),
            allowAlpha = allowAlpha,
            onDismiss = { picking = false },
            onPick = {
                onPick(it)
                picking = false
            },
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            // Checkerboard-ish backdrop so transparent colours are visible.
            .background(Color(0xFF3A3A3A))
            .background(color)
            .border(2.dp, if (selected) AtharColors.Gold else Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Hue / saturation / brightness (+ opacity) picker with a live sample and hex code. */
@Composable
fun ColorPickerSheet(initial: Int, allowAlpha: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    val argb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))
    var hex by remember(argb) { mutableStateOf(String.format("#%08X", argb)) }

    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.color_custom), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF3A3A3A))
                    .background(Color(argb))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(12.dp))
            GlassTextField(
                hex,
                { input ->
                    hex = input
                    parseHex(input)?.let { c ->
                        val out = FloatArray(3)
                        android.graphics.Color.colorToHSV(c, out)
                        hue = out[0]; sat = out[1]; value = out[2]
                        if (allowAlpha) alpha = android.graphics.Color.alpha(c) / 255f
                    }
                },
                "#FFFFFFFF",
                Modifier.weight(1f),
            )
        }
        PickerSlider(stringResource(R.string.color_hue), hue, 0f..360f, Brush.horizontalGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
        )) { hue = it }
        PickerSlider(stringResource(R.string.color_saturation), sat, 0f..1f, Brush.horizontalGradient(
            listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))),
        )) { sat = it }
        PickerSlider(stringResource(R.string.color_brightness), value, 0f..1f, Brush.horizontalGradient(
            listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))),
        )) { value = it }
        if (allowAlpha) {
            PickerSlider(stringResource(R.string.color_opacity), alpha, 0f..1f, Brush.horizontalGradient(
                listOf(Color.Transparent, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))),
            )) { alpha = it }
        }
        GlassButton(stringResource(R.string.done), { onPick(argb) }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PickerSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(track))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.padding(top = 0.dp),
        )
    }
}

/** "#RGB", "#RRGGBB" or "#AARRGGBB" → ARGB; null if not a colour. */
fun parseHex(input: String): Int? {
    val h = input.trim().removePrefix("#")
    if (h.isEmpty() || h.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return when (h.length) {
        3 -> (0xFF000000 or h.map { "$it$it" }.joinToString("").toLong(16)).toInt()
        6 -> (0xFF000000 or h.toLong(16)).toInt()
        8 -> h.toLong(16).toInt()
        else -> null
    }
}
