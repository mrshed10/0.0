package com.saudi.salariesapp.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val PresetColors = listOf(
    Color(0xFF007A5E), Color(0xFF00A896), Color(0xFF2E7D32), Color(0xFF1E88E5), Color(0xFF3949AB),
    Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFFE53935), Color(0xFFF4511E), Color(0xFFFB8C00),
    Color(0xFFFFD54F), Color(0xFFC0A062), Color(0xFF6D4C41), Color(0xFF546E7A), Color(0xFF212121),
    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFE0F2F1), Color(0xFFFFF8E1),
)

/** Swatch grid plus a free hex input (#RRGGBB) for any colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    label: String,
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hex by remember { mutableStateOf(selected.toHex()) }
    LaunchedEffect(selected) { hex = selected.toHex() }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).background(selected, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetColors.forEach { c ->
                val isSelected = c.toHex() == selected.toHex()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(c, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = if (c.luminance() > 0.5f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it.trim().take(7) },
                label = { Text("لون مخصص (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { parseHexOrNull(hex)?.let(onSelect) },
                enabled = parseHexOrNull(hex) != null,
            ) { Text("تطبيق") }
        }
    }
}

fun parseHexOrNull(hex: String): Color? {
    val h = if (hex.startsWith("#")) hex else "#$hex"
    if (!Regex("^#[0-9a-fA-F]{6}$").matches(h)) return null
    return parseHex(h)
}
