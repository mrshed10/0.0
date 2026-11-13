package com.livetranslate.overlay

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.livetranslate.overlay.translate.OcrPackStore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslate.overlay.data.Prefs
import com.livetranslate.overlay.a11y.TranslateAccessibilityService
import com.livetranslate.overlay.service.OverlayService
import com.livetranslate.overlay.tile.TranslateTileService
import com.livetranslate.overlay.translate.Language
import com.livetranslate.overlay.translate.Languages
import com.livetranslate.overlay.translate.ModelStore

/** شاشة الإعداد: الأذونات، اللغات، وتشغيل/إيقاف النافذة العائمة. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) {
                    darkColorScheme(primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF062E6F))
                } else {
                    lightColorScheme(primary = Color(0xFF1A73E8), background = Color(0xFFF7F8FC))
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SetupScreen()
                    }
                }
            }
        }
    }
}

private fun Context.hasNotificationPermission() =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val running by OverlayService.running.collectAsStateWithLifecycle()
    val translating by OverlayService.translating.collectAsStateWithLifecycle()
    val ocrInstalled by OcrPackStore.installed.collectAsStateWithLifecycle()
    val ocrDownloading by OcrPackStore.downloading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var bubbleEnabled by remember { mutableStateOf(prefs.bubbleEnabled) }
    val a11yConnected by TranslateAccessibilityService.connected.collectAsStateWithLifecycle()
    val downloaded by ModelStore.downloaded.collectAsStateWithLifecycle()
    val downloading by ModelStore.downloading.collectAsStateWithLifecycle()

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yEnabled by remember { mutableStateOf(TranslateAccessibilityService.isEnabled(context)) }
    var canNotify by remember { mutableStateOf(context.hasNotificationPermission()) }
    var source by remember { mutableStateOf(prefs.source) }
    var target by remember { mutableStateOf(prefs.target) }
    var modelError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    // إعادة الفحص عند العودة من شاشات إعدادات النظام
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(context)
        a11yEnabled = TranslateAccessibilityService.isEnabled(context)
        canNotify = context.hasNotificationPermission()
        source = prefs.source
        target = prefs.target
    }

    // تنزيل حزم اللغات المختارة مسبقاً، حتى تبدأ الترجمة فورياً دون أي تنزيل لاحق
    LaunchedEffect(source, target, retry) {
        modelError = false
        val modelsFailed = ModelStore.ensure(Languages.modelsFor(source, target))
        val ocrFailed = OcrPackStore.ensure(Languages.ocrPacksFor(source))
        modelError = modelsFailed.isNotEmpty() || ocrFailed.isNotEmpty()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    fun requestOverlay() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
    }

    fun openAccessibility() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()

        PermissionRow(
            icon = Icons.Rounded.Layers,
            title = stringResource(R.string.perm_overlay_title),
            body = stringResource(R.string.perm_overlay_body),
            granted = canOverlay,
            onGrant = ::requestOverlay,
        )
        PermissionRow(
            icon = Icons.Rounded.Accessibility,
            title = stringResource(R.string.perm_a11y_title),
            body = stringResource(R.string.perm_a11y_body),
            granted = a11yEnabled && a11yConnected,
            onGrant = ::openAccessibility,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.perm_notif_title),
                body = stringResource(R.string.perm_notif_body),
                granted = canNotify,
                onGrant = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        PermissionRow(
            icon = Icons.Rounded.Screenshot,
            title = stringResource(R.string.perm_capture_title),
            body = stringResource(R.string.perm_capture_body),
            granted = null,
            onGrant = {},
        )

        SectionCard {
            Text(stringResource(R.string.lang_section), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LanguageDropdown(stringResource(R.string.lang_source), Languages.sources, source) {
                source = it
                prefs.source = it
            }
            Spacer(Modifier.height(10.dp))
            LanguageDropdown(stringResource(R.string.lang_target), Languages.targets, target) {
                target = it
                prefs.target = it
            }
            Spacer(Modifier.height(12.dp))
            val ocrMissing = Languages.ocrPacksFor(source).any { it !in ocrInstalled }
            ModelStatus(
                needed = Languages.modelsFor(source, target),
                downloaded = downloaded,
                downloading = downloading ?: ocrDownloading?.let { pack ->
                    Languages.all.firstOrNull { it.tess == pack }?.code ?: pack
                },
                ocrMissing = ocrMissing,
                error = modelError,
                onRetry = { retry++ },
            )
            if (source == Languages.AUTO) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lang_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            Text(stringResource(R.string.extra_langs_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.extra_langs_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ExtraLanguages(
                installedPacks = ocrInstalled,
                downloadedModels = downloaded,
                busy = ocrDownloading != null || downloading != null,
                onAdd = { lang ->
                    scope.launch {
                        val tess = lang.tess ?: return@launch
                        OcrPackStore.ensure(listOf(tess))
                        ModelStore.ensure(listOf(lang.code))
                    }
                },
            )
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bubble_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.bubble_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = bubbleEnabled,
                    onCheckedChange = { on ->
                        if (on && !canOverlay && !a11yConnected) {
                            requestOverlay()
                            return@Switch
                        }
                        bubbleEnabled = on
                        prefs.bubbleEnabled = on
                        if (on) {
                            ContextCompat.startForegroundService(
                                context, OverlayService.intent(context, OverlayService.ACTION_SHOW_BUBBLE),
                            )
                        } else if (running) {
                            context.startService(OverlayService.intent(context, OverlayService.ACTION_HIDE_BUBBLE))
                        }
                    },
                )
            }
        }

        if (!translating) {
            Button(
                onClick = {
                    if (!canOverlay && !a11yConnected) {
                        requestOverlay()
                    } else {
                        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        context.startActivity(OverlayTrampolineActivity.startIntent(context))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.start_translation)) }
        } else {
            OutlinedButton(
                onClick = { context.startService(OverlayService.intent(context, OverlayService.ACTION_STOP_TRANSLATION)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.stop_translation)) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            FilledTonalButton(
                onClick = { requestAddTile(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.add_tile)) }
        }

        SectionCard {
            Text(stringResource(R.string.how_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.how_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModelStatus(
    needed: List<String>,
    downloaded: Set<String>,
    downloading: String?,
    ocrMissing: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
) {
    val missing = needed.filter { code -> ModelStore.tagOf(code)?.let { it !in downloaded && it != "en" } ?: false }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            downloading != null -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.models_downloading, Languages.nameOf(downloading)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            error || missing.isNotEmpty() || ocrMissing -> {
                Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.models_error),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.models_retry)) }
            }
            else -> {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF1E8E3E), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.models_ready),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, TranslateTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_stat_translate),
        ContextCompat.getMainExecutor(context),
    ) { }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraLanguages(
    installedPacks: Set<String>,
    downloadedModels: Set<String>,
    busy: Boolean,
    onAdd: (Language) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Languages.all.filter { it.tess != null }.forEach { lang ->
            val ready = lang.tess in installedPacks && lang.code in downloadedModels
            FilterChip(
                selected = ready,
                onClick = { if (!ready && !busy) onAdd(lang) },
                label = { Text(lang.nameAr) },
                leadingIcon = if (ready) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Translate, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.main_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean?,
    onGrant: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            when (granted) {
                true -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.perm_granted), tint = Color(0xFF1E8E3E))
                false -> FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
                null -> Text(
                    stringResource(R.string.perm_on_demand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(label: String, options: List<Language>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = Languages.nameOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.nameAr) },
                    onClick = {
                        onSelect(lang.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
