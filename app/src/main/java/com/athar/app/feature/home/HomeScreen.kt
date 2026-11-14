package com.athar.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.core.designsystem.glass.AuroraBackground
import com.athar.app.core.designsystem.glass.LocalHazeState
import com.athar.app.core.designsystem.theme.AtharTheme
import com.athar.app.feature.home.components.CountersSection
import com.athar.app.feature.home.components.DebtsCard
import com.athar.app.feature.home.components.HabitsCard
import com.athar.app.feature.home.components.HomeHeader
import com.athar.app.feature.home.components.NextPrayerCard
import com.athar.app.feature.home.components.SalaryCard
import com.athar.app.feature.home.components.TasbihCard
import com.athar.app.feature.home.components.TasksCard
import com.athar.app.feature.home.components.VerseCard
import com.athar.app.feature.tasks.Task
import com.athar.app.widget.WidgetContent
import com.athar.app.widget.WidgetType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDateTime

/** Navigation callbacks from the home screen. */
data class HomeNavigation(
    val onPinToWidget: (WidgetContent) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenCounters: () -> Unit = {},
    val onOpenSalaries: () -> Unit = {},
    val onOpenTasks: () -> Unit = {},
    val onOpenHabits: () -> Unit = {},
    val onOpenDebts: () -> Unit = {},
    val onOpenPrayer: () -> Unit = {},
)

@Composable
fun HomeRoute(
    onPinToWidget: (WidgetContent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCounters: () -> Unit,
    onOpenSalaries: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenDebts: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        nav = HomeNavigation(
            onPinToWidget, onOpenSettings, onOpenCounters, onOpenSalaries, onOpenTasks, onOpenHabits, onOpenDebts, onOpenPrayer,
        ),
        onToggleTask = viewModel::toggleTask,
        onToggleHabit = viewModel::toggleHabit,
        onTasbih = viewModel::incrementTasbih,
        onTasbihReset = viewModel::resetTasbih,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    nav: HomeNavigation,
    onToggleTask: (Task) -> Unit,
    onToggleHabit: (Long) -> Unit,
    onTasbih: () -> Unit,
    onTasbihReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { HomeHeader(state.today, nav.onOpenSettings) }
        state.nextPrayer?.let { next ->
            item(key = "prayer") {
                NextPrayerCard(
                    now = state.now,
                    prayers = state.prayers,
                    next = next,
                    cityId = state.cityId,
                    onPin = { nav.onPinToWidget(WidgetContent(WidgetType.NextPrayer)) },
                    onOpen = nav.onOpenPrayer,
                )
            }
        }
        item(key = "quick") {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SalaryCard(state.today, state.nextSalary, { nav.onPinToWidget(WidgetContent(WidgetType.Salary)) }, nav.onOpenSalaries, Modifier.weight(1f))
                TasbihCard(state.tasbih, onTasbih, onTasbihReset, Modifier.weight(1f))
            }
        }
        item(key = "counters") { CountersSection(state.today, state.counters, { id -> nav.onPinToWidget(WidgetContent(WidgetType.Counter, id)) }, nav.onOpenCounters) }
        item(key = "tasks") { TasksCard(state.todayTasks, state.today, onToggleTask, { nav.onPinToWidget(WidgetContent(WidgetType.TodayTasks)) }, nav.onOpenTasks) }
        item(key = "habits") { HabitsCard(state.habits, onToggleHabit, { nav.onPinToWidget(WidgetContent(WidgetType.Habits)) }, nav.onOpenHabits) }
        item(key = "debts") { DebtsCard(state.debts, { nav.onPinToWidget(WidgetContent(WidgetType.Debts)) }, nav.onOpenDebts) }
        item(key = "verse") { VerseCard(state.verse) }
    }
}

@Preview(locale = "ar", widthDp = 400, heightDp = 1600)
@Composable
private fun HomeScreenPreview() {
    val hazeState = remember { HazeState() }
    AtharTheme {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box {
                AuroraBackground(hazeState)
                HomeScreen(
                    state = HomeUiState(now = LocalDateTime.now()),
                    nav = HomeNavigation(),
                    onToggleTask = {},
                    onToggleHabit = {},
                    onTasbih = {},
                    onTasbihReset = {},
                )
            }
        }
    }
}
