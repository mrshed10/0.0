package com.saudi.salariesapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saudi.salariesapp.data.HijriCalendar
import java.time.LocalDate

/** حالة إدخال تاريخ يدعم التقويمين (هجري أم القرى / ميلادي). */
@Stable
class DualDateState(initial: LocalDate, hijri: Boolean) {
    var isHijri by mutableStateOf(hijri)
        private set
    var day by mutableStateOf("")
    var month by mutableStateOf(1)
    var year by mutableStateOf("")

    init {
        load(initial)
    }

    private fun load(date: LocalDate) {
        if (isHijri) {
            val h = HijriCalendar.toHijri(date)
            day = h.day.toString(); month = h.month; year = h.year.toString()
        } else {
            day = date.dayOfMonth.toString(); month = date.monthValue; year = date.year.toString()
        }
    }

    /** تبديل التقويم مع تحويل التاريخ المُدخل حالياً بدلاً من مسحه. */
    fun switchCalendar(toHijri: Boolean) {
        if (toHijri == isHijri) return
        val current = toLocalDate()
        isHijri = toHijri
        if (current != null) load(current)
    }

    /** null إذا كان التاريخ غير صالح. */
    fun toLocalDate(): LocalDate? {
        val d = day.toIntOrNull() ?: return null
        val y = year.toIntOrNull() ?: return null
        return if (isHijri) HijriCalendar.fromHijri(y, month, d)
        else runCatching { LocalDate.of(y, month, d) }.getOrNull()
    }
}

@Composable
fun rememberDualDateState(initial: LocalDate = LocalDate.now(), hijri: Boolean = false) =
    remember { DualDateState(initial, hijri) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualDateInput(state: DualDateState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !state.isHijri,
                onClick = { state.switchCalendar(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("ميلادي") }
            SegmentedButton(
                selected = state.isHijri,
                onClick = { state.switchCalendar(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("هجري") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.day,
                onValueChange = { state.day = it.filter(Char::isDigit).take(2) },
                label = { Text("اليوم") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.8f),
            )
            MonthDropdown(state, Modifier.weight(1.4f))
            OutlinedTextField(
                value = state.year,
                onValueChange = { state.year = it.filter(Char::isDigit).take(4) },
                label = { Text("السنة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        val date = state.toLocalDate()
        Text(
            text = when {
                date == null -> "⚠️ التاريخ غير صحيح"
                state.isHijri -> "يوافق: ${HijriCalendar.formatGregorian(date)}"
                else -> "يوافق: ${HijriCalendar.formatHijri(date)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (date == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthDropdown(state: DualDateState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val names = if (state.isHijri) HijriCalendar.hijriMonths else HijriCalendar.gregorianMonths
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(names[state.month - 1], maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEachIndexed { i, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { state.month = i + 1; expanded = false })
            }
        }
    }
}
