package com.athar.app.feature.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.TonePicker
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.ui.DualDatePicker

@Composable
fun CounterEditorSheet(
    initial: Counter,
    onDismiss: () -> Unit,
    onSave: (Counter) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val canSave = draft.title.isNotBlank()

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_counter else R.string.edit_counter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            placeholder = stringResource(R.string.counter_title_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        GlassSegmented(
            options = listOf(stringResource(R.string.counter_countdown), stringResource(R.string.counter_countup)),
            selectedIndex = draft.direction.ordinal,
            onSelect = { draft = draft.copy(direction = CounterDirection.entries[it]) },
            accent = draft.tone.color,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.date_label))
            DualDatePicker(
                date = draft.date,
                calendar = draft.calendar,
                onDateChange = { draft = draft.copy(date = it) },
                onCalendarChange = { draft = draft.copy(calendar = it) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.color_label))
            TonePicker(draft.tone, { draft = draft.copy(tone = it) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!initial.isNew) {
                GlassButton(
                    text = stringResource(R.string.delete),
                    onClick = { onDelete(initial.id) },
                    color = AtharColors.Rose,
                    filled = false,
                    icon = Icons.Rounded.DeleteOutline,
                )
            }
            GlassButton(
                text = stringResource(R.string.save),
                onClick = { onSave(draft) },
                enabled = canSave,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
