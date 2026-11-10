package com.saudi.salariesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saudi.salariesapp.ui.theme.PaletteHex
import com.saudi.salariesapp.ui.theme.parseHex

private val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")

/** منتقي ألوان: لوحة جاهزة + إدخال كود HEX لأي لون يريده المستخدم. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    label: String,
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hexInput by remember { mutableStateOf(selectedHex) }
    LaunchedEffect(selectedHex) { hexInput = selectedHex }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteHex.forEach { hex ->
                val selected = hex.equals(selectedHex, ignoreCase = true)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(parseHex(hex))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(hex) }
                )
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = {
                hexInput = it.trim()
                if (hexRegex.matches(hexInput)) onSelect(hexInput.uppercase())
            },
            label = { Text("كود اللون (مثل #007A5E)") },
            singleLine = true,
            isError = !hexRegex.matches(hexInput),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
