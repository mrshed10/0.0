package com.saudi.salariesapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saudi.salariesapp.notifications.ReminderScheduler
import com.saudi.salariesapp.ui.MainViewModel
import com.saudi.salariesapp.ui.components.AddCountdownDialog
import com.saudi.salariesapp.ui.screens.CountdownsScreen
import com.saudi.salariesapp.ui.screens.HomeScreen
import com.saudi.salariesapp.ui.screens.MoreScreen
import com.saudi.salariesapp.ui.screens.SalariesScreen
import com.saudi.salariesapp.ui.screens.ToolsScreen
import com.saudi.salariesapp.ui.theme.SalariesTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "الرئيسية", Icons.Filled.Home),
    SALARIES("salaries", "الرواتب", Icons.Filled.Payments),
    COUNTDOWNS("countdowns", "عداداتي", Icons.Filled.Timer),
    TOOLS("tools", "الأدوات", Icons.Filled.Calculate),
    MORE("more", "المزيد", Icons.Filled.MoreHoriz),
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.schedule(this)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SalariesTheme(themeMode = state.themeMode) {
                App(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { it.route == backStack?.destination?.route } ?: Tab.HOME
    var showAdd by remember { mutableStateOf(false) }

    // تحديث الأيام المتبقية عند العودة للتطبيق (قد يتغيّر اليوم)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val onNotify: (String, Boolean) -> Unit = { id, enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setNotify(id, enabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("مواعيد الرواتب السعودية", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (current == Tab.COUNTDOWNS) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة عداد")
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Tab.HOME.route) {
            composable(Tab.HOME.route) { HomeScreen(state, onAddCountdown = { showAdd = true }, contentPadding = padding) }
            composable(Tab.SALARIES.route) {
                SalariesScreen(
                    state = state,
                    contentPadding = padding,
                    onNotify = onNotify,
                    onPin = viewModel::setPinned,
                    onSetDay = viewModel::setPaymentDay,
                )
            }
            composable(Tab.COUNTDOWNS.route) {
                CountdownsScreen(state, padding, onDelete = viewModel::deleteCountdown, onPin = viewModel::setPinned)
            }
            composable(Tab.TOOLS.route) { ToolsScreen(padding) }
            composable(Tab.MORE.route) {
                MoreScreen(
                    state = state,
                    contentPadding = padding,
                    onSaveStyle = viewModel::saveWidgetStyle,
                    onPin = viewModel::setPinned,
                    onThemeMode = viewModel::setThemeMode,
                )
            }
        }
    }

    if (showAdd) {
        AddCountdownDialog(
            onDismiss = { showAdd = false },
            onSave = { viewModel.addCountdown(it); showAdd = false },
        )
    }
}
