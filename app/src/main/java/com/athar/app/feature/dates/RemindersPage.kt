package com.athar.app.feature.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatClock
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.notify.AlertPermissionCards
import com.athar.app.core.notify.ToneRow
import com.athar.app.core.ui.DualDatePicker
import com.athar.app.core.ui.TimeStepper
import com.athar.app.feature.reminders.Reminder
import com.athar.app.feature.reminders.Repeat
import java.time.LocalDateTime

@Composable
fun repeatLabel(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.Once -> R.string.repeat_once
        Repeat.Daily -> R.string.repeat_daily
        Repeat.Weekly -> R.string.repeat_weekly
        Repeat.Monthly -> R.string.repeat_monthly
        Repeat.Yearly -> R.string.repeat_yearly
    },
)

@Composable
fun RemindersRoute(switcher: @Composable () -> Unit, viewModel: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val locale = currentLocale()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = datesPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.reminders),
                    action = {
                        GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_reminder), {
                            editing = Reminder.new(LocalDateTime.now())
                        })
                    },
                )
            }
            item(key = "switcher") { switcher() }
            item(key = "perm") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AlertPermissionCards() } }
            if (state.loaded && state.reminders.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Alarm,
                        title = stringResource(R.string.empty_reminders_title),
                        body = stringResource(R.string.empty_reminders_body),
                        actionLabel = stringResource(R.string.add_reminder),
                        onAction = { editing = Reminder.new(LocalDateTime.now()) },
                    )
                }
            }
            items(state.reminders, key = { it.reminder.id }) { row ->
                val r = row.reminder
                GlassCard(
                    Modifier.fillMaxWidth(),
                    accent = if (r.enabled) AtharColors.Gold else null,
                    onClick = { editing = r },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val next = row.next
                            Text(
                                if (next != null) {
                                    next.toLocalDate().formatGregorian(locale, "EEE d MMM") + " · " + next.toLocalTime().formatClock(locale)
                                } else {
                                    stringResource(if (r.enabled) R.string.reminder_finished else R.string.reminder_paused)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (next != null) AtharColors.TextSecondary else AtharColors.TextTertiary,
                            )
                            if (next != null) {
                                Text(next.toLocalDate().formatHijri(), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
                            }
                            Spacer(Modifier.height(6.dp))
                            GlassChip(repeatLabel(r.repeat), AtharColors.Gold)
                        }
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = { viewModel.setReminderEnabled(r.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Gold),
                        )
                    }
                }
            }
            item(key = "tone") {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(stringResource(R.string.reminder_alerts), Icons.Rounded.MusicNote)
                    Spacer(Modifier.height(10.dp))
                    ToneRow(stringResource(R.string.default_tone), state.general?.reminderTone, viewModel::setReminderTone)
                }
            }
        }

        editing?.let { reminder ->
            ReminderEditorSheet(
                initial = reminder,
                onDismiss = { editing = null },
                onSave = { viewModel.saveReminder(it); editing = null },
                onDelete = { viewModel.deleteReminder(it); editing = null },
            )
        }
    }
}

@Composable
private fun ReminderEditorSheet(
    initial: Reminder,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_reminder else R.string.edit_reminder),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(draft.title, { draft = draft.copy(title = it) }, stringResource(R.string.reminder_title_hint), Modifier.fillMaxWidth())
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
            FieldLabel(stringResource(R.string.time_label))
            TimeStepper(draft.time, { draft = draft.copy(time = it) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.repeat_label))
            GlassSegmented(
                options = Repeat.entries.map { repeatLabel(it) },
                selectedIndex = draft.repeat.ordinal,
                onSelect = { draft = draft.copy(repeat = Repeat.entries[it]) },
            )
        }
        ToneRow(stringResource(R.string.tone_label), draft.tone, { draft = draft.copy(tone = it) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!initial.isNew) {
                ConfirmButton(
                    text = stringResource(R.string.delete),
                    confirmText = stringResource(R.string.tap_to_confirm),
                    onConfirm = { onDelete(initial.id) },
                    icon = Icons.Rounded.DeleteOutline,
                )
            }
            GlassButton(
                stringResource(R.string.save),
                { onSave(draft.copy(enabled = true)) },
                enabled = draft.title.isNotBlank(),
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
