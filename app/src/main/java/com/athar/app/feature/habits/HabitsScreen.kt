package com.athar.app.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.component.TonePicker
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatNumber
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun HabitsRoute(
    switcher: @Composable () -> Unit,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HabitsScreen(state, switcher, viewModel::toggle, viewModel::save, viewModel::delete)
}

@Composable
fun HabitsScreen(
    state: HabitsUiState,
    switcher: @Composable () -> Unit,
    onToggle: (Long, LocalDate) -> Unit,
    onSave: (Habit) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<Habit?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.habits),
                    subtitle = stringResource(
                        R.string.habits_done_today,
                        state.doneToday.formatNumber(),
                        state.habits.size.formatNumber(),
                    ),
                    action = { GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_habit), { editing = Habit.new() }) },
                )
            }
            item(key = "switcher") { switcher() }
            if (state.loaded && state.habits.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = stringResource(R.string.empty_habits_title),
                        body = stringResource(R.string.empty_habits_body),
                        actionLabel = stringResource(R.string.add_habit),
                        onAction = { editing = Habit.new() },
                    )
                }
            }
            items(state.habits, key = { it.habit.id }) { summary ->
                HabitCard(
                    summary = summary,
                    today = state.today,
                    onToggle = { day -> onToggle(summary.habit.id, day) },
                    onEdit = { editing = summary.habit },
                )
            }
        }

        editing?.let { habit ->
            HabitEditorSheet(
                initial = habit,
                onDismiss = { editing = null },
                onSave = { onSave(it); editing = null },
                onDelete = { onDelete(it); editing = null },
            )
        }
    }
}

@Composable
private fun HabitCard(
    summary: HabitSummary,
    today: LocalDate,
    onToggle: (LocalDate) -> Unit,
    onEdit: () -> Unit,
) {
    val tone = summary.habit.tone.color
    GlassCard(Modifier.fillMaxWidth(), accent = tone, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitBubble(summary.habit.icon, tone, summary.doneToday, onClick = { onToggle(today) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    summary.habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (summary.currentStreak > 0) AtharColors.Gold else AtharColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.streak_days, summary.currentStreak.formatNumber()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    stringResource(R.string.streak_best, summary.bestStreak.formatNumber()),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtharColors.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WeekStrip(summary.doneDays, today, tone, onToggle)
    }
}

/** Big round icon; filled when done. */
@Composable
fun HabitBubble(icon: HabitIcon, tone: Color, done: Boolean, onClick: () -> Unit, size: Int = 56) {
    val fill = if (done) {
        Modifier.background(tone)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.08f)).border(1.dp, tone.copy(alpha = 0.5f), CircleShape)
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).then(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) Icons.Rounded.Check else icon.vector(),
            contentDescription = null,
            tint = if (done) AtharColors.Night else AtharColors.TextPrimary,
        )
    }
}

/** Last 7 days; tap any day to fix a forgotten check-in. */
@Composable
private fun WeekStrip(done: Set<LocalDate>, today: LocalDate, tone: Color, onToggle: (LocalDate) -> Unit) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val isDone = day in done
            Column(
                Modifier.clip(CircleShape).clickable { onToggle(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) AtharColors.TextPrimary else AtharColors.TextTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDone) tone else Color.White.copy(alpha = 0.06f))
                        .border(1.dp, if (day == today) tone else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.formatNumber(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDone) AtharColors.Night else AtharColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorSheet(
    initial: Habit,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_habit else R.string.edit_habit),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            placeholder = stringResource(R.string.habit_title_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.icon_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitIcon.entries.forEach { icon ->
                    val selected = icon == draft.icon
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                            .padding(3.dp),
                    ) {
                        HabitBubble(icon, draft.tone.color, done = false, onClick = { draft = draft.copy(icon = icon) }, size = 44)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.color_label))
            TonePicker(draft.tone, { draft = draft.copy(tone = it) })
        }
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
                text = stringResource(R.string.save),
                onClick = { onSave(draft) },
                enabled = draft.title.isNotBlank(),
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
