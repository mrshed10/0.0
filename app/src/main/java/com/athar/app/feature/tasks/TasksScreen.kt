package com.athar.app.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import java.time.LocalDate

/** Due-date shortcut for quick add. */
enum class QuickDue { Today, Tomorrow, None }

@Composable
fun TasksRoute(
    switcher: @Composable () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksScreen(
        state = state,
        switcher = switcher,
        onQuickAdd = viewModel::quickAdd,
        onToggle = viewModel::toggle,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onClearCompleted = viewModel::clearCompleted,
    )
}

@Composable
fun TasksScreen(
    state: TasksUiState,
    switcher: @Composable () -> Unit,
    onQuickAdd: (String, LocalDate?) -> Unit,
    onToggle: (Task) -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Long) -> Unit,
    onClearCompleted: () -> Unit,
) {
    var editing by remember { mutableStateOf<Task?>(null) }
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
                    title = stringResource(R.string.nav_tasks),
                    subtitle = stringResource(R.string.tasks_done, state.doneCount.formatNumber(), state.total.formatNumber()),
                    action = {
                        GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_task), { editing = Task.new(state.today) })
                    },
                )
            }
            item(key = "switcher") { switcher() }
            item(key = "quickAdd") { QuickAddCard(state.today, onQuickAdd) }

            if (state.loaded && state.total == 0) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = stringResource(R.string.empty_tasks_title),
                        body = stringResource(R.string.empty_tasks_body),
                    )
                }
            }

            state.sections.forEach { (section, tasks) ->
                item(key = section.name) {
                    SectionCard(
                        section = section,
                        tasks = tasks,
                        today = state.today,
                        onToggle = onToggle,
                        onOpen = { editing = it },
                        onClearCompleted = onClearCompleted,
                    )
                }
            }
        }

        editing?.let { task ->
            TaskEditorSheet(
                initial = task,
                onDismiss = { editing = null },
                onSave = { onSave(it); editing = null },
                onDelete = { onDelete(it); editing = null },
            )
        }
    }
}

@Composable
private fun QuickAddCard(today: LocalDate, onQuickAdd: (String, LocalDate?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf(QuickDue.Today) }
    val submit = {
        val date = when (due) {
            QuickDue.Today -> today
            QuickDue.Tomorrow -> today.plusDays(1)
            QuickDue.None -> null
        }
        onQuickAdd(text.trim(), date)
        text = ""
    }

    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.quick_add_hint),
                onDone = submit,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add), submit)
        }
        Spacer(Modifier.height(10.dp))
        GlassSegmented(
            options = listOf(
                stringResource(R.string.today),
                stringResource(R.string.tomorrow),
                stringResource(R.string.due_none),
            ),
            selectedIndex = due.ordinal,
            onSelect = { due = QuickDue.entries[it] },
        )
    }
}

@Composable
private fun SectionCard(
    section: TaskSection,
    tasks: List<Task>,
    today: LocalDate,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onClearCompleted: () -> Unit,
) {
    val color = when (section) {
        TaskSection.Overdue -> AtharColors.Rose
        TaskSection.Today -> AtharColors.Gold
        TaskSection.Upcoming -> AtharColors.Teal
        TaskSection.Someday -> AtharColors.Violet
        TaskSection.Done -> AtharColors.TextTertiary
    }
    GlassCard(Modifier.fillMaxWidth(), accent = color.takeIf { section != TaskSection.Done }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(section.label()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (section == TaskSection.Done) AtharColors.TextSecondary else Color.Unspecified,
            )
            Spacer(Modifier.width(8.dp))
            GlassChip(tasks.size.formatNumber(), color)
            Spacer(Modifier.weight(1f))
            if (section == TaskSection.Done) {
                Text(
                    stringResource(R.string.clear_completed),
                    style = MaterialTheme.typography.labelLarge,
                    color = AtharColors.Rose,
                    modifier = Modifier.clickable(onClick = onClearCompleted),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        tasks.forEach { task ->
            TaskRow(
                task = task,
                today = today,
                onToggle = { onToggle(task) },
                showDue = section != TaskSection.Today,
                onClick = { onOpen(task) },
            )
        }
    }
}

private fun TaskSection.label(): Int = when (this) {
    TaskSection.Overdue -> R.string.section_overdue
    TaskSection.Today -> R.string.today
    TaskSection.Upcoming -> R.string.section_upcoming
    TaskSection.Someday -> R.string.due_none
    TaskSection.Done -> R.string.section_done
}
