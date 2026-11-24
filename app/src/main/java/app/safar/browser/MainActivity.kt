package app.safar.browser

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.ui.browser.BookmarksScreen
import app.safar.browser.ui.browser.BrowserScreen
import app.safar.browser.ui.browser.ClearDataDialog
import app.safar.browser.ui.browser.HistoryScreen
import app.safar.browser.ui.settings.DnsSettingsScreen
import app.safar.browser.ui.settings.NewTabSettingsScreen
import app.safar.browser.ui.extensions.ExtensionsScreen
import app.safar.browser.ui.onboarding.OnboardingScreen
import app.safar.browser.ui.settings.SettingsScreen
import app.safar.browser.ui.settings.ToolbarCustomizeScreen
import app.safar.browser.ui.theme.SafarTheme
import org.mozilla.geckoview.GeckoView
import java.lang.ref.WeakReference

enum class Screen { BROWSER, SETTINGS, TOOLBAR, EXTENSIONS, BOOKMARKS, HISTORY, DNS, NEWTAB }

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels()

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.activity = WeakReference(this)
        if (savedInstanceState == null) vm.handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            var screen by remember { mutableStateOf(Screen.BROWSER) }
            val privateTab = vm.tabs.selected?.isPrivate == true

            SafarTheme(privateMode = privateTab && screen == Screen.BROWSER) {
                if (!settings.onboardingDone) {
                    OnboardingScreen(
                        initial = settings,
                        onFinish = { s, makeDefault ->
                            vm.updateSettings { s.copy(onboardingDone = true) }
                            requestNotifications()
                            if (makeDefault) requestDefaultBrowser()
                        },
                    )
                    return@SafarTheme
                }

                BackHandler(enabled = screen != Screen.BROWSER) {
                    screen = if (screen == Screen.TOOLBAR || screen == Screen.DNS) Screen.SETTINGS else Screen.BROWSER
                }

                when (screen) {
                    Screen.BROWSER -> BrowserScreen(
                        vm = vm,
                        openSettings = { screen = Screen.SETTINGS },
                        openExtensions = { screen = Screen.EXTENSIONS },
                        openBookmarks = { screen = Screen.BOOKMARKS },
                        openHistory = { screen = Screen.HISTORY },
                        openNewTabSettings = { screen = Screen.NEWTAB },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        vm = vm,
                        onBack = { screen = Screen.BROWSER },
                        openToolbar = { screen = Screen.TOOLBAR },
                        requestDefaultBrowser = ::requestDefaultBrowser,
                        openDns = { screen = Screen.DNS },
                        openNewTab = { screen = Screen.NEWTAB },
                    )
                    Screen.TOOLBAR -> ToolbarCustomizeScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
                    Screen.EXTENSIONS -> ExtensionsScreen(vm = vm, onBack = { screen = Screen.BROWSER })
                    Screen.HISTORY -> {
                        var clear by remember { mutableStateOf(false) }
                        HistoryScreen(vm = vm, onBack = { screen = Screen.BROWSER }, onOpen = {
                            vm.load(it); screen = Screen.BROWSER
                        }, onClearData = { clear = true })
                        if (clear) ClearDataDialog(onDismiss = { clear = false }, onConfirm = vm::clearBrowsingData)
                    }
                    Screen.DNS -> DnsSettingsScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
                    Screen.NEWTAB -> NewTabSettingsScreen(vm = vm, onBack = { screen = Screen.BROWSER })
                    Screen.BOOKMARKS -> BookmarksScreen(vm = vm, onBack = { screen = Screen.BROWSER }, onOpen = {
                        vm.load(it); screen = Screen.BROWSER
                    })
                }

                // تأكيد تثبيت إضافة (أذوناتها)
                val install by vm.installRequest.collectAsStateWithLifecycle()
                install?.let { req ->
                    AlertDialog(
                        onDismissRequest = { req.answer.complete(false) },
                        title = { Text("تثبيت ${req.name}؟") },
                        text = {
                            Text(
                                if (req.permissions.isEmpty()) "لا تطلب هذه الإضافة أذونات خاصة."
                                else "تطلب الأذونات التالية:\n• " + req.permissions.joinToString("\n• ")
                            )
                        },
                        confirmButton = { TextButton(onClick = { req.answer.complete(true) }) { Text("تثبيت") } },
                        dismissButton = { TextButton(onClick = { req.answer.complete(false) }) { Text("إلغاء") } },
                    )
                }

                // نافذة الإضافة المنبثقة (popup)
                val popup by vm.popupSession.collectAsStateWithLifecycle()
                popup?.let { session ->
                    ModalBottomSheet(onDismissRequest = { session.close(); vm.popupSession.value = null }) {
                        Column {
                            AndroidView(
                                factory = { ctx -> GeckoView(ctx).apply { setSession(session) } },
                                modifier = Modifier.fillMaxWidth().height(480.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onStart() { super.onStart(); vm.onActivityStart() }
    override fun onStop() { vm.onActivityStop(); super.onStop() }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** تعيين المتصفح كافتراضي: RoleManager على Android 10+، وإلا شاشة التطبيقات الافتراضية. */
    fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }
}
