package com.athar.app.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.AuroraBackground
import com.athar.app.core.designsystem.glass.GlassBarItem
import com.athar.app.core.designsystem.glass.GlassBottomBar
import com.athar.app.core.designsystem.glass.BottomBarController
import com.athar.app.core.designsystem.glass.LocalBottomBarController
import com.athar.app.core.designsystem.glass.LocalHazeState
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.feature.counters.CountersRoute
import com.athar.app.feature.debts.DebtsRoute
import com.athar.app.feature.habits.HabitsRoute
import com.athar.app.feature.settings.SettingsRoute
import com.athar.app.feature.tasks.TasksRoute
import com.athar.app.feature.home.HomeRoute
import com.athar.app.AtharApplication
import com.athar.app.widget.WidgetContent
import com.athar.app.widget.WidgetPinner
import com.athar.app.core.region.LocalCurrency
import com.athar.app.feature.dates.RemindersRoute
import com.athar.app.feature.dates.SalariesRoute
import com.athar.app.feature.prayer.PrayerTabRoute
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

enum class AtharTab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Dates(R.string.nav_dates, Icons.Rounded.HourglassTop),
    Tasks(R.string.nav_tasks, Icons.Rounded.TaskAlt),
    Debts(R.string.nav_debts, Icons.Rounded.AccountBalanceWallet),
    Prayer(R.string.nav_prayer, Icons.Rounded.Mosque),
}

/** App shell: aurora background (blur source), current tab, floating glass bar. */
@Composable
fun AtharApp(startTab: String? = null) {
    val hazeState = remember { HazeState() }
    var tab by rememberSaveable {
        mutableStateOf(
            when (startTab) {
                "dates", "salaries" -> AtharTab.Dates
                "tasks", "habits" -> AtharTab.Tasks
                "debts" -> AtharTab.Debts
                "prayer" -> AtharTab.Prayer
                else -> AtharTab.Home
            },
        )
    }
    var tasksPage by rememberSaveable { mutableIntStateOf(if (startTab == "habits") 1 else 0) }
    var datesPage by rememberSaveable { mutableIntStateOf(if (startTab == "salaries") 1 else 0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pinContext = LocalContext.current
    val pinRequested = stringResource(R.string.pin_requested)
    val pinUnsupported = stringResource(R.string.pin_unsupported)
    val pinWidget: (WidgetContent) -> Unit = { content ->
        val ok = WidgetPinner.pin(pinContext, content)
        scope.launch { snackbar.showSnackbar(if (ok) pinRequested else pinUnsupported) }
    }

    val bottomBar = remember { BottomBarController() }

    val context = LocalContext.current
    val general by (context.applicationContext as AtharApplication).container.settings.general.collectAsState()

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomBarController provides bottomBar,
        LocalCurrency provides stringResource(general.country.currency),
    ) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(hazeState)

            if (showSettings) {
                SettingsRoute(onBack = { showSettings = false })
            } else {
                when (tab) {
                    AtharTab.Home -> HomeRoute(
                        onPinToWidget = pinWidget,
                        onOpenSettings = { showSettings = true },
                        onOpenCounters = { datesPage = 0; tab = AtharTab.Dates },
                        onOpenSalaries = { datesPage = 1; tab = AtharTab.Dates },
                        onOpenPrayer = { tab = AtharTab.Prayer },
                        onOpenTasks = { tasksPage = 0; tab = AtharTab.Tasks },
                        onOpenHabits = { tasksPage = 1; tab = AtharTab.Tasks },
                        onOpenDebts = { tab = AtharTab.Debts },
                    )
                    AtharTab.Dates -> {
                        val switcher: @Composable () -> Unit = {
                            GlassSegmented(
                                options = listOf(
                                    stringResource(R.string.counters),
                                    stringResource(R.string.salaries),
                                    stringResource(R.string.reminders),
                                ),
                                selectedIndex = datesPage,
                                onSelect = { datesPage = it },
                            )
                        }
                        when (datesPage) {
                            0 -> CountersRoute(switcher)
                            1 -> SalariesRoute(switcher)
                            else -> RemindersRoute(switcher)
                        }
                    }
                    AtharTab.Tasks -> {
                        val switcher: @Composable () -> Unit = {
                            GlassSegmented(
                                options = listOf(stringResource(R.string.nav_tasks), stringResource(R.string.habits)),
                                selectedIndex = tasksPage,
                                onSelect = { tasksPage = it },
                            )
                        }
                        if (tasksPage == 0) TasksRoute(switcher) else HabitsRoute(switcher)
                    }
                    AtharTab.Debts -> DebtsRoute()
                    AtharTab.Prayer -> PrayerTabRoute()
                }
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            )
            AnimatedVisibility(
                visible = bottomBar.visible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassBottomBar(
                    items = AtharTab.entries.map { GlassBarItem(stringResource(it.label), it.icon) },
                    selectedIndex = tab.ordinal,
                    onSelect = { showSettings = false; tab = AtharTab.entries[it] },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
