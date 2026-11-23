package app.safar.browser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.AddressBarPosition
import app.safar.browser.data.GestureAction
import app.safar.browser.data.OverlayStyle
import app.safar.browser.data.ToolbarItem
import app.safar.browser.data.TranslateDisplayMode
import app.safar.browser.ui.translate.FloatingGlassBox
import app.safar.browser.ui.translate.TranslatePanel
import app.safar.browser.ui.translate.TranslateStatusChip

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    openSettings: () -> Unit,
    openExtensions: () -> Unit,
    openBookmarks: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.translate.status.collectAsStateWithLifecycle()
    val installed by vm.extensions.installed.collectAsStateWithLifecycle()
    val tab = vm.tabs.selected

    var showTabs by remember { mutableStateOf(false) }
    var showVertical by remember { mutableStateOf(false) }
    var showTranslate by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = tab?.canGoBack == true) { tab?.session?.goBack() }
    BackHandler(enabled = tab?.fullscreen == true) { tab?.session?.exitFullScreen() }

    val fullscreen = tab?.fullscreen == true
    val onHorizontal: (Int) -> Unit = { dir ->
        when (settings.barSwipe) {
            GestureAction.SWITCH_TABS -> vm.tabs.selectRelative(dir)
            GestureAction.BACK_FORWARD -> if (dir > 0) tab?.session?.goForward() else tab?.session?.goBack()
            GestureAction.NONE -> Unit
        }
    }
    val gestures = Modifier.barGestures(
        enabled = settings.barSwipe != GestureAction.NONE,
        onHorizontal = onHorizontal,
        onSwipeUp = if (settings.swipeUpOpensTabs) ({ showTabs = true }) else null,
    )

    val addressBar = @Composable {
        AddressBar(
            tab = tab,
            siteExcepted = vm.currentHostExcepted(),
            onSubmit = vm::load,
            onShieldClick = vm::toggleSiteException,
            modifier = gestures,
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()
    ) {
        if (!fullscreen && settings.addressBar == AddressBarPosition.TOP) { addressBar(); PageProgress(tab) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            GeckoViewHost(tab, onViewReady = { vm.translate.geckoView = it })

            if (settings.translate.enabled) {
                TranslateStatusChip(
                    status = status,
                    overlaysVisible = settings.translate.overlaysVisible,
                    onClick = { showTranslate = true },
                    onToggleVisible = { vm.updateTranslate { it.copy(overlaysVisible = !it.overlaysVisible) } },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                if (settings.translate.mode == TranslateDisplayMode.FLOATING) {
                    FloatingGlassBox(
                        onTranslate = { rect -> vm.translate.translateRegion(rect, vm.tabs.selected?.session) },
                        opacity = settings.translate.overlayOpacity,
                        style = settings.translate.overlayStyle,
                        visible = settings.translate.overlaysVisible,
                        onOpacityChange = { v -> vm.updateTranslate { it.copy(overlayOpacity = v) } },
                        onStyleCycle = {
                            vm.updateTranslate {
                                val all = OverlayStyle.entries
                                it.copy(overlayStyle = all[(all.indexOf(it.overlayStyle) + 1) % all.size])
                            }
                        },
                        onToggleVisible = { vm.updateTranslate { it.copy(overlaysVisible = !it.overlaysVisible) } },
                        onClose = { vm.updateTranslate { it.copy(mode = TranslateDisplayMode.CONTINUOUS, enabled = false) } },
                    )
                }
            }

            VerticalTabsPanel(
                visible = showVertical,
                tabs = vm.tabs.tabs,
                selectedId = vm.tabs.selectedId,
                onSelect = vm.tabs::select,
                onClose = vm::closeTab,
                onNew = { vm.newTab() },
                onDismiss = { showVertical = false },
            )
        }

        if (!fullscreen) {
            if (settings.addressBar == AddressBarPosition.BOTTOM) { PageProgress(tab); addressBar() }
            Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(0.dp)) {
                BrowserToolbar(
                    items = settings.toolbarItems,
                    canGoBack = tab?.canGoBack == true,
                    canGoForward = tab?.canGoForward == true,
                    tabCount = vm.tabs.tabs.size,
                    translateOn = settings.translate.enabled,
                    pinned = installed.filter { it.id in settings.pinnedExtensions && it.enabled },
                    onExtensionClick = vm.extensions::click,
                    modifier = gestures,
                    onAction = { item ->
                        when (item) {
                            ToolbarItem.BACK -> tab?.session?.goBack()
                            ToolbarItem.FORWARD -> tab?.session?.goForward()
                            ToolbarItem.HOME -> vm.goHome()
                            ToolbarItem.RELOAD -> tab?.session?.reload()
                            ToolbarItem.TABS_COUNTER -> showTabs = true
                            ToolbarItem.VERTICAL_TABS -> showVertical = !showVertical
                            ToolbarItem.INCOGNITO -> vm.newTab(private = true)
                            ToolbarItem.EXTENSIONS -> openExtensions()
                            ToolbarItem.LIVE_TRANSLATE -> showTranslate = true
                            ToolbarItem.BOOKMARKS -> openBookmarks()
                            ToolbarItem.SHARE -> vm.share()
                            ToolbarItem.MENU -> menuOpen = true
                        }
                    },
                    menu = {
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val close = { menuOpen = false }
                            MenuItem("تبويب جديد", close) { vm.newTab() }
                            MenuItem("تبويب متخفٍ جديد", close) { vm.newTab(private = true) }
                            MenuItem(
                                if (tab != null && vm.isBookmarked(tab.url)) "إزالة الإشارة المرجعية" else "إضافة إشارة مرجعية", close,
                            ) { vm.toggleBookmark() }
                            MenuItem("الإشارات المرجعية", close) { openBookmarks() }
                            MenuItem("الترجمة الحية", close) { showTranslate = true }
                            MenuItem(
                                if (vm.currentHostExcepted()) "تفعيل الحماية لهذا الموقع" else "استثناء هذا الموقع من الحماية", close,
                            ) { vm.toggleSiteException() }
                            MenuItem("مشاركة", close) { vm.share() }
                            HorizontalDivider()
                            MenuItem("الإضافات", close) { openExtensions() }
                            MenuItem("الإعدادات", close) { openSettings() }
                        }
                    },
                )
            }
        }
    }

    if (showTabs) {
        TabsSheet(
            tabs = vm.tabs.tabs,
            selectedId = vm.tabs.selectedId,
            onSelect = vm.tabs::select,
            onClose = vm::closeTab,
            onNew = { private -> vm.newTab(private) },
            onCloseAllPrivate = { vm.tabs.tabs.filter { it.isPrivate }.forEach { vm.closeTab(it.id) } },
            onDismiss = { showTabs = false },
        )
    }

    if (showTranslate) {
        TranslatePanel(vm = vm, onDismiss = { showTranslate = false })
    }
}

@Composable
private fun MenuItem(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { close(); action() })
}
