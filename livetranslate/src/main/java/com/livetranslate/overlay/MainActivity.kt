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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.livetranslate.overlay.overlay.EdgeGlowPainter
import com.livetranslate.overlay.service.OverlayService
import com.livetranslate.overlay.tile.TranslateTileService
import com.livetranslate.overlay.translate.Language
import com.livetranslate.overlay.translate.Languages

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

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canNotify by remember { mutableStateOf(context.hasNotificationPermission()) }
    var source by remember { mutableStateOf(prefs.source) }
    var target by remember { mutableStateOf(prefs.target) }
    var glowAnimated by remember { mutableStateOf(prefs.glowAnimated) }

    // إعادة الفحص عند العودة من شاشة إعدادات النظام
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(context)
        canNotify = context.hasNotificationPermission()
        source = prefs.source
        target = prefs.target
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    fun requestOverlay() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlowHeader()

        PermissionRow(
            icon = Icons.Rounded.Layers,
            title = stringResource(R.string.perm_overlay_title),
            body = stringResource(R.string.perm_overlay_body),
            granted = canOverlay,
            onGrant = ::requestOverlay,
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
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.glow_animated), Modifier.weight(1f))
                Switch(checked = glowAnimated, onCheckedChange = {
                    glowAnimated = it
                    prefs.glowAnimated = it
                })
            }
        }

        if (!running) {
            Button(
                onClick = {
                    if (!canOverlay) {
                        requestOverlay()
                    } else {
                        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ContextCompat.startForegroundService(
                            context, OverlayService.intent(context, OverlayService.ACTION_SHOW),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.start_overlay)) }
        } else {
            OutlinedButton(
                onClick = { context.startService(OverlayService.intent(context, OverlayService.ACTION_STOP)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.stop_overlay)) }
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

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, TranslateTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_stat_translate),
        ContextCompat.getMainExecutor(context),
    ) { }
}

/** معاينة حيّة للإطار المضيء باستخدام نفس الرسّام المستخدم فوق الشاشة. */
@Composable
private fun GlowHeader() {
    val density = LocalDensity.current.density
    val painter = remember { EdgeGlowPainter(density) }
    val rotation by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                painter.drawPreview(it.nativeCanvas, size.width, size.height, 28.dp.toPx(), rotation)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Rounded.Translate, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
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
