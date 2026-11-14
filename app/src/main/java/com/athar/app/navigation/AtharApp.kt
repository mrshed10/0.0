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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.AuroraBackground
import com.athar.app.core.designsystem.glass.GlassBarItem
import com.athar.app.core.designsystem.glass.GlassBottomBar
import com.athar.app.core.designsystem.glass.BottomBarController
import com.athar.app.core.designsystem.glass.LocalBottomBarController
import com.athar.app.core.designsystem.glass.LocalHazeState
import com.athar.app.feature.counters.CountersRoute
import com.athar.app.feature.tasks.TasksRoute
import com.athar.app.feature.home.HomeRoute
import com.athar.app.feature.placeholder.ComingSoonScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

enum class AtharTab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Counters(R.string.nav_counters, Icons.Rounded.HourglassTop),
    Tasks(R.string.nav_tasks, Icons.Rounded.TaskAlt),
    Debts(R.string.nav_debts, Icons.Rounded.AccountBalanceWallet),
    Prayer(R.string.nav_prayer, Icons.Rounded.Mosque),
}

/** App shell: aurora background (blur source), current tab, floating glass bar. */
@Composable
fun AtharApp() {
    val hazeState = remember { HazeState() }
    var tab by rememberSaveable { mutableStateOf(AtharTab.Home) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pinMessage = stringResource(R.string.pin_to_widget_soon)
    val showPinMessage: () -> Unit = { scope.launch { snackbar.showSnackbar(pinMessage) } }

    val bottomBar = remember { BottomBarController() }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomBarController provides bottomBar,
    ) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(hazeState)

            when (tab) {
                AtharTab.Home -> HomeRoute(
                    onPinToWidget = showPinMessage,
                    onOpenSettings = {},
                    onOpenCounters = { tab = AtharTab.Counters },
                    onOpenTasks = { tab = AtharTab.Tasks },
                )
                AtharTab.Counters -> CountersRoute()
                AtharTab.Tasks -> TasksRoute()
                else -> ComingSoonScreen(stringResource(tab.label), tab.icon)
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
                    onSelect = { tab = AtharTab.entries[it] },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
