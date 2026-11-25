package app.safar.browser.ui.browser

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.safar.browser.data.QuickAction
import app.safar.browser.data.effectiveQuickAction
import app.safar.browser.extensions.ChromeStore
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
    openHistory: () -> Unit,
    openNewTabSettings: () -> Unit,
    openReadingList: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val speaking by vm.pageReader.speaking.collectAsStateWithLifecycle()
    var showRecent by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var showStore by remember { mutableStateOf(false) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.load(it) }
    }
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

    val runQuick: (QuickAction) -> Unit = { a ->
        vm.countAction(a)
        when (a) {
            QuickAction.NEW_TAB, QuickAction.AUTO -> vm.newTab()
            QuickAction.SHARE -> vm.share()
            QuickAction.VOICE_SEARCH -> runCatching {
                voiceLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدّث للبحث")
                )
            }
            QuickAction.TRANSLATE -> showTranslate = true
            QuickAction.BOOKMARK -> vm.toggleBookmark()
            QuickAction.READ_ALOUD -> vm.readAloud()
        }
    }

    val addressBar = @Composable {
        AddressBar(
            tab = tab,
            siteExcepted = vm.currentHostExcepted(),
            onSubmit = vm::load,
            onShieldClick = vm::toggleSiteException,
            modifier = gestures,
            quickAction = if (settings.quickActionEnabled) {
                {
                    val qa = settings.effectiveQuickAction()
                    QuickActionButton(qa, speaking) { runQuick(qa) }
                }
            } else null,
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding()
    ) {
        if (!fullscreen && settings.addressBar == AddressBarPosition.TOP) { addressBar(); PageProgress(tab) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            GeckoViewHost(tab, onViewReady = { vm.translate.geckoView = it })

            // صفحة علامة التبويب الجديدة الأصلية
            if (tab != null && tab.url == "about:blank" && !tab.loading) {
                NewTabPage(settings.newTab, bookmarks, history, onOpen = vm::load, onCustomize = openNewTabSettings)
            }
            // صفحة إضافة في متجر Chrome ⇒ زر "إضافة إلى سفر"
            if (ChromeStore.extensionId(tab?.url) != null) {
                WebStoreBanner(onClick = { showStore = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }

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
                        onInteractionStart = { vm.translate.clearFloatingOverlays(vm.tabs.selected?.session) },
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
                        MainMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            canGoBack = tab?.canGoBack == true,
                            canGoForward = tab?.canGoForward == true,
                            bookmarked = tab != null && bookmarks.any { it.url == tab.url },
                            desktopMode = tab?.desktopMode == true,
                            showZoom = settings.showZoomInMenu,
                            zoom = vm.currentZoom(),
                            siteExcepted = vm.currentHostExcepted(),
                            a = MenuActions(
                                reload = { tab?.session?.reload() },
                                savePage = { vm.savePage() },
                                toggleBookmark = { vm.toggleBookmark(); vm.countAction(QuickAction.BOOKMARK) },
                                back = { tab?.session?.goBack() },
                                forward = { tab?.session?.goForward() },
                                newTab = { vm.newTab(); vm.countAction(QuickAction.NEW_TAB) },
                                newPrivateTab = { vm.newTab(private = true) },
                                addToNewGroup = vm::addCurrentToNewGroup,
                                history = openHistory,
                                clearData = { showClearData = true },
                                downloads = vm::openDownloads,
                                bookmarks = openBookmarks,
                                readingList = openReadingList,
                                recentTabs = { showRecent = true },
                                translate = { showTranslate = true; vm.countAction(QuickAction.TRANSLATE) },
                                toggleDesktop = vm::toggleDesktopMode,
                                readAloud = { vm.readAloud(); vm.countAction(QuickAction.READ_ALOUD) },
                                share = { vm.share(); vm.countAction(QuickAction.SHARE) },
                                siteShield = vm::toggleSiteException,
                                zoom = vm::setZoom,
                                extensions = openExtensions,
                                webStore = vm::openChromeWebStore,
                                settings = openSettings,
                                customizeNewTab = openNewTabSettings,
                            ),
                        )
                    },
                )
            }
        }
    }

    if (showTabs) {
        TabsSheet(
            tabs = vm.tabs.tabs,
            groups = vm.tabs.groups,
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
    val linkTarget by vm.linkTarget.collectAsStateWithLifecycle()
    val previewUrl by vm.previewUrl.collectAsStateWithLifecycle()
    linkTarget?.let { t ->
        LinkContextSheet(
            t,
            LinkActions(
                newTab = vm::openLinkInNewTab,
                newTabInGroup = vm::openLinkInGroup,
                incognito = vm::openLinkIncognito,
                newWindow = vm::openLinkInNewWindow,
                preview = { vm.previewUrl.value = it },
                copyAddress = { vm.copyToClipboard("رابط", it) },
                copyText = { vm.copyToClipboard("نص الرابط", it) },
                download = { vm.downloadLink(it) },
                readingList = { u, title -> vm.addToReadingList(u, title) },
                share = vm::shareLink,
            ),
            onDismiss = { vm.linkTarget.value = null },
        )
    }
    previewUrl?.let { u ->
        PagePreviewSheet(u, newSession = { vm.newPreviewSession(it, tab?.isPrivate == true) }, onOpen = vm::openLinkInNewTab) {
            vm.previewUrl.value = null
        }
    }
    if (showRecent) RecentTabsSheet(vm.tabs.recentlyClosed, vm::reopenClosed) { showRecent = false }
    if (showClearData) ClearDataDialog(onDismiss = { showClearData = false }, onConfirm = vm::clearBrowsingData)
    if (showStore) WebStoreSheet(analyze = vm::analyzeStoreExtension, onInstall = vm::installExtension) { showStore = false }
}

