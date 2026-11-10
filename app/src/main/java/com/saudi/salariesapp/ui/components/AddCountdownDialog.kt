package com.saudi.salariesapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saudi.salariesapp.data.CalendarType
import com.saudi.salariesapp.data.Category
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.HijriDate
import com.saudi.salariesapp.data.HijriDates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Create a personal countdown: title, Gregorian or Hijri (Umm al-Qura) date, time and colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCountdownDialog(onDismiss: () -> Unit, onSave: (EventModel) -> Unit) {
    val today = remember { LocalDate.now() }
    var title by remember { mutableStateOf("") }
    var calendar by remember { mutableStateOf(CalendarType.GREGORIAN) }
    var date by remember { mutableStateOf(today.plusDays(7)) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var color by remember { mutableStateOf(parseHex(Category.CUSTOM.defaultColorHex)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عداد تنازلي جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المناسبة (اختبار، زواج، سداد فاتورة…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = calendar == CalendarType.GREGORIAN,
                        onClick = { calendar = CalendarType.GREGORIAN },
                        label = { Text("ميلادي") },
                    )
                    FilterChip(
                        selected = calendar == CalendarType.HIJRI,
                        onClick = { calendar = CalendarType.HIJRI },
                        label = { Text("هجري") },
                    )
                }

                if (calendar == CalendarType.GREGORIAN) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(HijriDates.formatGregorian(date))
                    }
                } else {
                    HijriDateSelector(date = date, onChange = { date = it })
                }
                Text(
                    text = if (calendar == CalendarType.GREGORIAN) "يوافق ${HijriDates.formatHijri(date)}"
                    else "يوافق ${HijriDates.formatGregorian(date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("الوقت: " + String.format(Locale.US, "%02d:%02d", time.hour, time.minute))
                }

                ColorPicker(label = "لون البطاقة", selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !date.isBefore(today),
                onClick = {
                    onSave(
                        EventModel(
                            id = "",
                            title = title.trim(),
                            target = date.atTime(time),
                            category = Category.CUSTOM,
                            colorHex = color.toHex(),
                            calendarType = calendar,
                            isCustom = true,
                        )
                    )
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("اختر الوقت") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("إلغاء") } },
        )
    }
}

/** Day / month / year pickers in the Umm al-Qura calendar. */
@Composable
fun HijriDateSelector(date: LocalDate, onChange: (LocalDate) -> Unit, yearsBack: Int = 0, yearsAhead: Int = 10) {
    val h = HijriDates.toHijri(date)
    val currentYear = remember { HijriDates.toHijri(LocalDate.now()).year }
    val firstYear = currentYear - yearsBack

    fun emit(year: Int, month: Int, day: Int) {
        val safeDay = day.coerceAtMost(HijriDates.lengthOfMonth(year, month))
        onChange(HijriDates.toGregorian(HijriDate(year, month, safeDay)))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Dropdown(
            label = h.day.toString(),
            options = (1..HijriDates.lengthOfMonth(h.year, h.month)).map { it.toString() },
            onSelect = { emit(h.year, h.month, it + 1) },
            modifier = Modifier.weight(0.8f),
        )
        Dropdown(
            label = HijriDates.hijriMonths[h.month - 1],
            options = HijriDates.hijriMonths,
            onSelect = { emit(h.year, it + 1, h.day) },
            modifier = Modifier.weight(1.6f),
        )
        Dropdown(
            label = h.year.toString(),
            options = (firstYear..currentYear + yearsAhead).map { it.toString() },
            onSelect = { emit(firstYear + it, h.month, h.day) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun Dropdown(label: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(i)
                })
            }
        }
    }
}
