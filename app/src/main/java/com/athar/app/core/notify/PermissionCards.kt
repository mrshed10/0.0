package com.athar.app.core.notify

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors

/**
 * Shown only when something blocks notifications: the notification
 * permission (Android 13+) or exact alarms (Android 12).
 */
@Composable
fun AlertPermissionCards(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    // Re-check whenever the user returns from system settings.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ }
    }
    val canNotify = remember(refresh) { Notifier.canPost(context) }
    val scheduler = (context.applicationContext as AtharApplication).container.alarmScheduler
    val canExact = remember(refresh) { scheduler.canScheduleExact() }

    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }

    if (!canNotify) {
        GlassCard(modifier.fillMaxWidth(), accent = AtharColors.Rose) {
            CardHeader(stringResource(R.string.perm_notifications_title), Icons.Rounded.NotificationsActive, tint = AtharColors.Rose)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.perm_notifications_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            GlassButton(stringResource(R.string.perm_allow), {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }, color = AtharColors.Rose)
        }
    }
    if (!canExact && Build.VERSION.SDK_INT >= 31) {
        GlassCard(modifier.fillMaxWidth(), accent = AtharColors.Gold) {
            CardHeader(stringResource(R.string.perm_exact_title), Icons.Rounded.AlarmOn)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.perm_exact_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            GlassButton(stringResource(R.string.perm_allow), {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                )
            })
        }
    }
}
