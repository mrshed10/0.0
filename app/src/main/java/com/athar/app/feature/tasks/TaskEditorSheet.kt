package com.athar.app.feature.tasks

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
import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.ui.DualDatePicker
import java.time.LocalDate

private enum class DueChoice { None, Today, Tomorrow, Pick }

@Composable
fun TaskEditorSheet(
    initial: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var choice by remember(initial.id) {
        mutableStateOf(
            when (initial.due) {
                null -> DueChoice.None
                today -> DueChoice.Today
                today.plusDays(1) -> DueChoice.Tomorrow
                else -> DueChoice.Pick
            },
        )
    }
    var picked by remember(initial.id) { mutableStateOf(initial.due ?: today) }
    var calendar by remember { mutableStateOf(CalendarKind.Gregorian) }

    val due = when (choice) {
        DueChoice.None -> null
        DueChoice.Today -> today
        DueChoice.Tomorrow -> today.plusDays(1)
        DueChoice.Pick -> picked
    }

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_task else R.string.edit_task),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = stringResource(R.string.task_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.date_label))
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.due_none),
                    stringResource(R.string.today),
                    stringResource(R.string.tomorrow),
                    stringResource(R.string.due_pick),
                ),
                selectedIndex = choice.ordinal,
                onSelect = { choice = DueChoice.entries[it] },
            )
            if (choice == DueChoice.Pick) {
                DualDatePicker(
                    date = picked,
                    calendar = calendar,
                    onDateChange = { picked = it },
                    onCalendarChange = { calendar = it },
                )
            }
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
                onClick = { onSave(initial.copy(title = title, due = due)) },
                enabled = title.isNotBlank(),
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
