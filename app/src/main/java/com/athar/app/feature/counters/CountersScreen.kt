package com.athar.app.feature.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HourglassTop
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
import com.athar.app.core.designsystem.component.EmptyState
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.ScreenHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.displayName
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.format.formatNumber
import java.time.LocalDate

@Composable
fun CountersRoute(viewModel: CountersViewModel = viewModel(factory = CountersViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CountersScreen(state, viewModel::setFilter, viewModel::save, viewModel::delete)
}

@Composable
fun CountersScreen(
    state: CountersUiState,
    onFilter: (CounterFilter) -> Unit,
    onSave: (Counter) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<Counter?>(null) }
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
                    title = stringResource(R.string.nav_counters),
                    subtitle = stringResource(R.string.counters_count, state.totalCount.formatNumber()),
                    action = {
                        GlassIconButton(Icons.Rounded.Add, stringResource(R.string.add_counter), { editing = Counter.new(state.today) })
                    },
                )
            }
            item(key = "filter") {
                GlassSegmented(
                    options = listOf(
                        stringResource(R.string.filter_all),
                        stringResource(R.string.counter_countdown),
                        stringResource(R.string.counter_countup),
                    ),
                    selectedIndex = state.filter.ordinal,
                    onSelect = { onFilter(CounterFilter.entries[it]) },
                )
            }
            if (state.loaded && state.counters.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.HourglassTop,
                        title = stringResource(R.string.empty_counters_title),
                        body = stringResource(R.string.empty_counters_body),
                        actionLabel = stringResource(R.string.add_counter),
                        onAction = { editing = Counter.new(state.today) },
                    )
                }
            }
            items(state.counters, key = { it.id }) { counter ->
                CounterRow(counter, state.today, onClick = { editing = counter })
            }
        }

        editing?.let { counter ->
            CounterEditorSheet(
                initial = counter,
                onDismiss = { editing = null },
                onSave = { onSave(it); editing = null },
                onDelete = { onDelete(it); editing = null },
            )
        }
    }
}

@Composable
private fun CounterRow(counter: Counter, today: LocalDate, onClick: () -> Unit) {
    val locale = currentLocale()
    val reading = CounterMath.read(counter, today)
    val tone = counter.tone.color
    val gregorian = counter.date.formatGregorian(locale)
    val hijri = counter.date.formatHijri()
    val (first, second) = if (counter.calendar == CalendarKind.Hijri) hijri to gregorian else gregorian to hijri

    GlassCard(Modifier.fillMaxWidth(), accent = tone, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    counter.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(reading.label(), style = MaterialTheme.typography.labelLarge, color = tone)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${counter.date.dayOfWeek.displayName(locale)} · $first",
                    style = MaterialTheme.typography.labelMedium,
                    color = AtharColors.TextSecondary,
                )
                Text(second, style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (reading.days == 0L) {
                    Text(
                        stringResource(R.string.today),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone,
                    )
                } else {
                    Text(
                        reading.days.formatNumber(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.unit_days),
                        style = MaterialTheme.typography.labelMedium,
                        color = AtharColors.TextSecondary,
                    )
                }
            }
        }
    }
}
