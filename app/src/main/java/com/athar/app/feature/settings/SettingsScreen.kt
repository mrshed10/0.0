package com.athar.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.app.BuildConfig
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import com.athar.app.core.security.BiometricAuth
import java.time.LocalDate

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenGallery: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportPassword by remember { mutableStateOf("") }
    var widgetMessage by remember { mutableStateOf<String?>(null) }
    var fontMessage by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri, exportPassword)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val lockTitle = stringResource(R.string.debts_lock)
    val lockSubtitle = stringResource(R.string.debts_lock_confirm)

    BackHandler(onBack = onBack)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            item(key = "backup") {
                GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
                    CardHeader(stringResource(R.string.backup_title), Icons.Rounded.Backup, tint = AtharColors.Teal)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.backup_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtharColors.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldLabel(stringResource(R.string.backup_password_label))
                    Spacer(Modifier.height(6.dp))
                    GlassTextField(
                        exportPassword,
                        { exportPassword = it },
                        stringResource(R.string.backup_password_hint),
                        Modifier.fillMaxWidth(),
                        isPassword = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(
                            stringResource(R.string.backup_create),
                            { createFile.launch("athar-backup-${LocalDate.now()}.json") },
                            color = AtharColors.Teal,
                            enabled = !state.working,
                            icon = Icons.Rounded.Save,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            stringResource(R.string.backup_restore),
                            { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                            color = AtharColors.Teal,
                            filled = false,
                            enabled = !state.working,
                            icon = Icons.Rounded.Restore,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    state.status?.let { status ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(status.text, status.arg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.isError) AtharColors.Rose else AtharColors.Teal,
                        )
                    }
                }
            }

            item(key = "fonts") {
                Column {
                    FontsSettingsCard(onMessage = { fontMessage = it })
                    fontMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AtharColors.Rose, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item(key = "widgets") {
                Column {
                    WidgetsSettingsCard(onMessage = { widgetMessage = it }, onOpenGallery = onOpenGallery)
                    widgetMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AtharColors.Gold, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item(key = "chime") { ChimeCard() }

            item(key = "sounds") { com.athar.app.core.notify.MySoundsCard() }

            item(key = "security") {
                val available = remember { BiometricAuth.isAvailable(context) }
                GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
                    CardHeader(stringResource(R.string.security_title), Icons.Rounded.Fingerprint)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lockTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (available) R.string.debts_lock_body else R.string.debts_lock_unavailable),
                                style = MaterialTheme.typography.labelMedium,
                                color = AtharColors.TextTertiary,
                            )
                        }
                        Switch(
                            checked = state.debtsLock,
                            enabled = available,
                            onCheckedChange = { wanted ->
                                // Changing the lock always requires passing it once.
                                BiometricAuth.authenticate(
                                    context,
                                    lockTitle,
                                    lockSubtitle,
                                    onSuccess = { viewModel.setDebtsLock(wanted) },
                                    onError = {},
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AtharColors.Night,
                                checkedTrackColor = AtharColors.Gold,
                            ),
                        )
                    }
                }
            }

            item(key = "crash") { CrashReportCard() }

            item(key = "about") {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(stringResource(R.string.about_title), Icons.Rounded.Info, tint = AtharColors.Violet)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_body, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtharColors.TextSecondary,
                    )
                }
            }
        }

        state.pending?.let { pending ->
            RestoreSheet(
                pending = pending,
                working = state.working,
                status = state.status,
                onUnlock = viewModel::unlockPending,
                onConfirm = viewModel::confirmRestore,
                onDismiss = viewModel::cancelRestore,
            )
        }
    }
}

@Composable
private fun RestoreSheet(
    pending: PendingRestore,
    working: Boolean,
    status: StatusMessage?,
    onUnlock: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val data = pending.data
        if (data == null) {
            Text(stringResource(R.string.backup_encrypted_prompt), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
            GlassTextField(
                password,
                { password = it },
                stringResource(R.string.backup_password_hint),
                Modifier.fillMaxWidth(),
                onDone = { onUnlock(password) },
                isPassword = true,
            )
            if (status?.isError == true) {
                Text(stringResource(status.text, status.arg), style = MaterialTheme.typography.labelMedium, color = AtharColors.Rose)
            }
            GlassButton(
                stringResource(R.string.unlock),
                { onUnlock(password) },
                enabled = password.isNotEmpty() && !working,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(
                    R.string.backup_contents,
                    data.counters.size.formatNumber(),
                    data.tasks.size.formatNumber(),
                    data.habits.size.formatNumber(),
                    data.people.size.formatNumber(),
                    data.debtEntries.size.formatNumber(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.backup_replace_warning), style = MaterialTheme.typography.bodyMedium, color = AtharColors.Rose)
            ConfirmButton(
                text = stringResource(R.string.backup_restore_now),
                confirmText = stringResource(R.string.tap_to_confirm),
                onConfirm = onConfirm,
                icon = Icons.Rounded.Restore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
