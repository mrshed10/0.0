package com.athar.app.feature.dates

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Widgets
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.athar.app.widget.WidgetContent
import com.athar.app.widget.WidgetPinner
import com.athar.app.widget.WidgetType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.R
import com.athar.app.core.calendar.CalendarKind
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
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.format.formatNumber
import com.athar.app.core.notify.AlertPermissionCards
import com.athar.app.core.notify.ToneRow
import com.athar.app.core.region.CountryPickerSheet
import com.athar.app.core.ui.Stepper
import com.athar.app.feature.salary.SalarySource
import com.athar.app.feature.salary.WeekendShift
import java.time.temporal.ChronoUnit

@Composable
internal fun datesPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 120.dp)
}

@Composable
fun SalariesRoute(switcher: @Composable () -> Unit, viewModel: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SalarySource?>(null) }
    var countrySheet by remember { mutableStateOf(false) }
    val general = state.general
    val context = LocalContext.current
    val pinRequested = stringResource(R.string.pin_requested)
    val pinUnsupported = stringResource(R.string.pin_unsupported)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = datesPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.salaries),
                    subtitle = general?.let { "${it.country.flag} " + stringResource(it.country.label) },
                    action = {
                        Row {
                            GlassIconButton(Icons.Rounded.Widgets, stringResource(R.string.pin_schedule_widget), {
                                val ok = WidgetPinner.pin(context, WidgetContent(WidgetType.Schedule))
                                Toast.makeText(context, if (ok) pinRequested else pinUnsupported, Toast.LENGTH_LONG).show()
                            })
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_salary), {
                                editing = SalarySource(name = "", payDay = 27)
                            })
                        }
                    },
                )
            }
            item(key = "switcher") { switcher() }
            item(key = "country") {
                GlassCard(Modifier.fillMaxWidth(), onClick = { countrySheet = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Public, contentDescription = null, tint = AtharColors.Teal)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.country_label), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                            Text(
                                general?.let { "${it.country.flag}  " + stringResource(it.country.label) }.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.salary_estimate_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
                }
            }
            if (state.loaded && state.salaries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Payments,
                        title = stringResource(R.string.empty_salaries_title),
                        body = stringResource(R.string.empty_salaries_body),
                        actionLabel = stringResource(R.string.add_salary),
                        onAction = { editing = SalarySource(name = "", payDay = 27) },
                    )
                }
            }
            items(state.salaries, key = { it.source.id }) { row ->
                SalaryCard(row, state.now.toLocalDate(), onClick = { editing = row.source })
            }
            item(key = "perm") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AlertPermissionCards() } }
            item(key = "tone") {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(stringResource(R.string.salary_alerts), Icons.Rounded.MusicNote)
                    Spacer(Modifier.height(10.dp))
                    ToneRow(stringResource(R.string.default_tone), general?.salaryTone, viewModel::setSalaryTone)
                }
            }
        }

        editing?.let { source ->
            SalaryEditorSheet(
                initial = source,
                onDismiss = { editing = null },
                onSave = { viewModel.saveSalary(it); editing = null },
                onDelete = { viewModel.deleteSalary(it); editing = null },
            )
        }
        if (countrySheet && general != null) {
            CountryPickerSheet(
                selected = general.country,
                onSelect = { viewModel.setCountry(it); countrySheet = false },
                onDismiss = { countrySheet = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalaryCard(row: SalaryRow, today: java.time.LocalDate, onClick: () -> Unit) {
    val locale = currentLocale()
    val days = ChronoUnit.DAYS.between(today, row.next)
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.source.remindDaysBefore >= 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = AtharColors.Gold, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    row.next.formatGregorian(locale, "EEEE d MMMM"),
                    style = MaterialTheme.typography.labelLarge,
                    color = AtharColors.TextSecondary,
                )
                Text(row.next.formatHijri(), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (days == 0L) {
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AtharColors.Teal)
                } else {
                    Text(days.formatNumber(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.unit_days), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            row.upcoming.drop(1).forEach { GlassChip(it.formatGregorian(locale, "d MMM"), AtharColors.Teal) }
        }
    }
}

@Composable
private fun SalaryEditorSheet(
    initial: SalarySource,
    onDismiss: () -> Unit,
    onSave: (SalarySource) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val reminderOptions = listOf(-1, 0, 1, 3)

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_salary else R.string.edit_salary),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(draft.name, { draft = draft.copy(name = it) }, stringResource(R.string.salary_name_hint), Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.pay_day_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(
                    stringResource(R.string.day_label),
                    draft.payDay.formatNumber(),
                    { n -> draft = draft.copy(payDay = (draft.payDay + n.toInt() - 1).mod(31) + 1) },
                    Modifier.weight(1f),
                )
                Box(Modifier.weight(1.4f)) {
                    GlassSegmented(
                        options = listOf(stringResource(R.string.calendar_gregorian), stringResource(R.string.calendar_hijri)),
                        selectedIndex = draft.calendar.ordinal,
                        onSelect = { draft = draft.copy(calendar = CalendarKind.entries[it]) },
                        accent = AtharColors.Teal,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.weekend_rule))
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.shift_before),
                    stringResource(R.string.shift_after),
                    stringResource(R.string.shift_none),
                ),
                selectedIndex = draft.shift.ordinal,
                onSelect = { draft = draft.copy(shift = WeekendShift.entries[it]) },
                accent = AtharColors.Teal,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.reminder_label))
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.off),
                    stringResource(R.string.on_the_day),
                    stringResource(R.string.day_before),
                    stringResource(R.string.three_days_before),
                ),
                selectedIndex = reminderOptions.indexOf(draft.remindDaysBefore).coerceAtLeast(0),
                onSelect = { draft = draft.copy(remindDaysBefore = reminderOptions[it]) },
            )
            if (draft.remindDaysBefore >= 0) {
                ToneRow(stringResource(R.string.tone_label), draft.tone, { draft = draft.copy(tone = it) })
            }
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
                stringResource(R.string.save),
                { onSave(draft) },
                enabled = draft.name.isNotBlank(),
                color = AtharColors.Teal,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
