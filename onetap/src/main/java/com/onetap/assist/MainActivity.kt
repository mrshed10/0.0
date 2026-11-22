package com.onetap.assist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onetap.assist.overlay.OverlayService
import com.onetap.assist.ui.ComponentPickerDialog
import com.onetap.assist.ui.CustomUriDialog
import com.onetap.assist.ui.OneTapTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OneTapTheme { SetupScreen() } }
    }
}

private enum class Dialog { NONE, FALLBACK_MENU, APPS, SHORTCUTS, CUSTOM_URI }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    // --- permission status, refreshed every time we come back from Settings ---
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var notifGranted by remember { mutableStateOf(notificationsGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(ctx)
        notifGranted = notificationsGranted(ctx)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    val serviceState by OverlayService.state.collectAsStateWithLifecycle()

    // --- settings state mirrors SharedPreferences; the service listens for changes ---
    var size by remember { mutableFloatStateOf(prefs.sizeDp.toFloat()) }
    var opacity by remember { mutableFloatStateOf(prefs.opacity) }
    var snap by remember { mutableStateOf(prefs.snapToEdge) }
    var autoHide by remember { mutableStateOf(prefs.autoHideScreenOff) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var action by remember { mutableStateOf(prefs.tapAction) }
    var autoFallback by remember { mutableStateOf(prefs.autoFallback) }
    var fallbackLabel by remember { mutableStateOf(prefs.fallbackLabel) }
    var dialog by remember { mutableStateOf(Dialog.NONE) }

    fun setFallback(intent: Intent?, label: String?) {
        prefs.setFallback(intent?.toUri(Intent.URI_INTENT_SCHEME), label)
        fallbackLabel = prefs.fallbackLabel
    }

    val shortcutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data ?: return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val target = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        @Suppress("DEPRECATION")
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        if (target != null) {
            setFallback(target, name ?: target.component?.packageName ?: "Shortcut")
        } else {
            Toast.makeText(ctx, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------- Setup / status ----------------
            SectionCard(stringResource(R.string.section_setup)) {
                PermissionRow(
                    title = stringResource(R.string.perm_overlay_title),
                    desc = stringResource(R.string.perm_overlay_desc),
                    granted = canOverlay,
                    missingLabel = stringResource(R.string.status_required),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    PermissionRow(
                        title = stringResource(R.string.perm_notif_title),
                        desc = stringResource(R.string.perm_notif_desc),
                        granted = notifGranted,
                        missingLabel = stringResource(R.string.status_off),
                        actionLabel = stringResource(R.string.action_allow),
                        optional = true,
                        onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                val stateText = when (serviceState) {
                    OverlayService.State.VISIBLE -> R.string.button_state_running
                    OverlayService.State.HIDDEN -> R.string.button_state_hidden
                    OverlayService.State.STOPPED -> R.string.button_state_stopped
                }
                Text(
                    stringResource(stateText),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (serviceState == OverlayService.State.STOPPED) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (serviceState) {
                        OverlayService.State.STOPPED -> Button(
                            enabled = canOverlay,
                            onClick = { OverlayService.send(ctx, OverlayService.ACTION_START) },
                        ) { Text(stringResource(R.string.action_start)) }

                        OverlayService.State.VISIBLE -> {
                            FilledTonalButton(onClick = { OverlayService.send(ctx, OverlayService.ACTION_HIDE) }) {
                                Text(stringResource(R.string.action_hide))
                            }
                            OutlinedButton(onClick = { OverlayService.stop(ctx) }) { Text(stringResource(R.string.action_stop)) }
                        }

                        OverlayService.State.HIDDEN -> {
                            Button(onClick = { OverlayService.send(ctx, OverlayService.ACTION_SHOW) }) {
                                Text(stringResource(R.string.action_show))
                            }
                            OutlinedButton(onClick = { OverlayService.stop(ctx) }) { Text(stringResource(R.string.action_stop)) }
                        }
                    }
                }
                if (!canOverlay) {
                    Text(
                        stringResource(R.string.need_overlay_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = { AssistLauncher.trigger(ctx, prefs) }) { Text(stringResource(R.string.action_test)) }
            }

            // ---------------- Appearance ----------------
            SectionCard(stringResource(R.string.section_appearance)) {
                Text(stringResource(R.string.setting_size, size.roundToInt()))
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    onValueChangeFinished = { prefs.sizeDp = size.roundToInt() },
                    valueRange = Prefs.MIN_SIZE.toFloat()..Prefs.MAX_SIZE.toFloat(),
                    steps = (Prefs.MAX_SIZE - Prefs.MIN_SIZE) / 4 - 1,
                )
                Text(stringResource(R.string.setting_opacity, (opacity * 100).roundToInt()))
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it; prefs.opacity = it },
                    valueRange = Prefs.MIN_OPACITY..1f,
                )
                SwitchRow(stringResource(R.string.setting_snap), stringResource(R.string.setting_snap_desc), snap) {
                    snap = it; prefs.snapToEdge = it
                }
                SwitchRow(stringResource(R.string.setting_autohide), stringResource(R.string.setting_autohide_desc), autoHide) {
                    autoHide = it; prefs.autoHideScreenOff = it
                }
                SwitchRow(stringResource(R.string.setting_haptics), null, haptics) {
                    haptics = it; prefs.haptics = it
                }
            }

            // ---------------- Tap action ----------------
            SectionCard(stringResource(R.string.section_action)) {
                Column(Modifier.selectableGroup()) {
                    ActionOption(TapAction.ASSIST, action, R.string.tap_assist, R.string.tap_assist_desc) {
                        action = it; prefs.tapAction = it
                    }
                    ActionOption(TapAction.VOICE, action, R.string.tap_voice, R.string.tap_voice_desc) {
                        action = it; prefs.tapAction = it
                    }
                    ActionOption(TapAction.SEARCH, action, R.string.tap_search, R.string.tap_search_desc) {
                        action = it; prefs.tapAction = it
                    }
                    ActionOption(TapAction.FALLBACK_ONLY, action, R.string.tap_fallback_only, R.string.tap_fallback_only_desc) {
                        action = it; prefs.tapAction = it
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (action != TapAction.FALLBACK_ONLY) {
                    SwitchRow(stringResource(R.string.setting_autofallback), null, autoFallback) {
                        autoFallback = it; prefs.autoFallback = it
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fallback_target), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            fallbackLabel ?: stringResource(R.string.fallback_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { dialog = Dialog.FALLBACK_MENU }) {
                        Text(stringResource(R.string.action_change))
                    }
                }
            }

            // ---------------- Help ----------------
            SectionCard(stringResource(R.string.section_help)) {
                Text(stringResource(R.string.help_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(12.dp))
                Row {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.help_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    // ---------------- Dialogs ----------------
    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.FALLBACK_MENU -> FallbackMenu(
            hasFallback = fallbackLabel != null,
            onSelect = { dialog = it },
            onLens = {
                setFallback(AssistLauncher.lensIntent(), ctx.getString(R.string.fallback_pick_lens))
                dialog = Dialog.NONE
            },
            onClear = { setFallback(null, null); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )

        Dialog.APPS -> ComponentPickerDialog(
            title = stringResource(R.string.pick_app_title),
            intent = remember { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER) },
            onPick = { item ->
                setFallback(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(item.component),
                    item.label,
                )
                dialog = Dialog.NONE
            },
            onDismiss = { dialog = Dialog.NONE },
        )

        Dialog.SHORTCUTS -> ComponentPickerDialog(
            title = stringResource(R.string.pick_shortcut_title),
            intent = remember { Intent(Intent.ACTION_CREATE_SHORTCUT) },
            onPick = { item ->
                dialog = Dialog.NONE
                runCatching { shortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(item.component)) }
            },
            onDismiss = { dialog = Dialog.NONE },
        )

        Dialog.CUSTOM_URI -> CustomUriDialog(
            initial = prefs.fallbackUri ?: "",
            onSave = { uri ->
                prefs.setFallback(uri, ctx.getString(R.string.custom_uri_label) + ": " + uri.take(40))
                fallbackLabel = prefs.fallbackLabel
                dialog = Dialog.NONE
            },
            onDismiss = { dialog = Dialog.NONE },
        )
    }
}

@Composable
private fun FallbackMenu(
    hasFallback: Boolean,
    onSelect: (Dialog) -> Unit,
    onLens: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fallback_pick_title)) },
        text = {
            Column {
                MenuItem(stringResource(R.string.fallback_pick_app)) { onSelect(Dialog.APPS) }
                MenuItem(stringResource(R.string.fallback_pick_shortcut)) { onSelect(Dialog.SHORTCUTS) }
                MenuItem(stringResource(R.string.fallback_pick_lens), onLens)
                MenuItem(stringResource(R.string.fallback_pick_uri)) { onSelect(Dialog.CUSTOM_URI) }
                if (hasFallback) MenuItem(stringResource(R.string.fallback_clear), onClear)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    missingLabel: String,
    actionLabel: String,
    optional: Boolean = false,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (icon: ImageVector, tint: Color) = when {
            granted -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            optional -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
            else -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        }
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) stringResource(R.string.status_granted) else "$missingLabel · $desc",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            if (optional) OutlinedButton(onClick = onAction) { Text(actionLabel) }
            else Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionOption(value: TapAction, selected: TapAction, title: Int, desc: Int, onSelect: (TapAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun notificationsGranted(ctx: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
