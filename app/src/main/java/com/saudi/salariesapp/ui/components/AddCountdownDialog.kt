package com.saudi.salariesapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saudi.salariesapp.data.CustomCountdown
import java.time.LocalDate
import java.time.LocalTime

private val suggestions = listOf("موعد شخصي", "اختبارات", "زواج", "سداد فاتورة", "إجازة", "سفر")

/** نافذة إنشاء عداد تنازلي مخصص لأي تاريخ (هجري أو ميلادي) ووقت. */
@Composable
fun AddCountdownDialog(
    onDismiss: () -> Unit,
    onSave: (CustomCountdown) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("09") }
    var minute by remember { mutableStateOf("00") }
    var color by remember { mutableStateOf("#007A5E") }
    val dateState = rememberDualDateState(LocalDate.now().plusDays(7))

    val date = dateState.toLocalDate()
    val time = runCatching { LocalTime.of(hour.toInt(), minute.toInt()) }.getOrNull()
    val valid = title.isNotBlank() && date != null && time != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عداد تنازلي جديد") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("عنوان المناسبة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(3).forEach { s -> AssistChip(onClick = { title = s }, label = { Text(s) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.drop(3).forEach { s -> AssistChip(onClick = { title = s }, label = { Text(s) }) }
                }
                DualDateInput(dateState)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("الساعة (0-23)") },
                        singleLine = true,
                        isError = hour.toIntOrNull()?.let { it in 0..23 } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("الدقيقة") },
                        singleLine = true,
                        isError = minute.toIntOrNull()?.let { it in 0..59 } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                ColorPicker("لون البطاقة", color, { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        CustomCountdown(
                            id = "",
                            title = title.trim(),
                            target = date!!.atTime(time!!),
                            colorHex = color,
                            enteredAsHijri = dateState.isHijri,
                        )
                    )
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
