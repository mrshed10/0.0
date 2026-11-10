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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.data.Category
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.ui.components.AddCountdownDialog
import com.saudi.salariesapp.ui.screens.AgeCalculatorScreen
import com.saudi.salariesapp.ui.screens.DateConverterScreen
import com.saudi.salariesapp.ui.screens.HomeScreen
import com.saudi.salariesapp.ui.screens.MoreRoutes
import com.saudi.salariesapp.ui.screens.MoreScreen
import com.saudi.salariesapp.ui.screens.OccasionsScreen
import com.saudi.salariesapp.ui.screens.ScheduleScreen
import com.saudi.salariesapp.ui.screens.SettingsScreen
import com.saudi.salariesapp.ui.screens.VatCalculatorScreen
import com.saudi.salariesapp.ui.screens.WidgetEditorScreen
import com.saudi.salariesapp.ui.theme.SalariesTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by viewModel.darkMode.collectAsStateWithLifecycle()
            SalariesTheme(darkOverride = dark) {
                App(viewModel)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Filled.Home),
    Tab("salaries", "الرواتب", Icons.Filled.DateRange),
    Tab("retirement", "التقاعد", Icons.Filled.Face),
    Tab("citizen", "حساب المواطن", Icons.Filled.Person),
    Tab("more", "المزيد", Icons.Filled.Menu),
)

private val titles = mapOf(
    "home" to "مواعيد الرواتب",
    "salaries" to "الرواتب والدعم",
    "retirement" to "التقاعد",
    "citizen" to "حساب المواطن",
    "more" to "المزيد",
    MoreRoutes.OCCASIONS to "المناسبات",
    MoreRoutes.AGE to "حساب العمر",
    MoreRoutes.CONVERTER to "محول التاريخ",
    MoreRoutes.VAT to "حاسبة الفواتير والأسعار",
    MoreRoutes.WIDGET to "تخصيص الودجت",
    MoreRoutes.SETTINGS to "الإعدادات",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val isTab = tabs.any { it.route == route }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    // Ask once for notification permission (Android 13+) so salary reminders can be shown.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var askedPermission by remember { mutableStateOf(false) }
    LaunchedEffect(reminders) {
        if (reminders.isNotEmpty() && !askedPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
                actions = {
                    if (route == "home") {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "إضافة عداد")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "home") {
            composable("home") { HomeScreen(vm, padding, onAddCountdown = { showAdd = true }) }
            composable("salaries") {
                ScheduleScreen(
                    vm,
                    PaymentSchedule.rulesFor(Category.SALARIES, Category.SOCIAL_SECURITY, Category.SAKANI, Category.REHAB),
                    padding,
                )
            }
            composable("retirement") { ScheduleScreen(vm, PaymentSchedule.rulesFor(Category.RETIREMENT), padding) }
            composable("citizen") { ScheduleScreen(vm, PaymentSchedule.rulesFor(Category.CITIZEN), padding) }
            composable("more") { MoreScreen(padding, onOpen = { nav.navigate(it) }) }
            composable(MoreRoutes.OCCASIONS) { OccasionsScreen(vm, padding) }
            composable(MoreRoutes.AGE) { AgeCalculatorScreen(padding) }
            composable(MoreRoutes.CONVERTER) { DateConverterScreen(padding) }
            composable(MoreRoutes.VAT) { VatCalculatorScreen(padding) }
            composable(MoreRoutes.WIDGET) { WidgetEditorScreen(vm, padding) }
            composable(MoreRoutes.SETTINGS) { SettingsScreen(vm, padding) }
        }
    }

    if (showAdd) {
        AddCountdownDialog(
            onDismiss = { showAdd = false },
            onSave = {
                vm.addCountdown(it)
                showAdd = false
            },
        )
    }
}
