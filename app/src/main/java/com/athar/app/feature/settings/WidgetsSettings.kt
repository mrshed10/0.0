package com.athar.app.feature.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import com.athar.app.core.notify.Notifier
import com.athar.app.widget.LockCard
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.widget.WidgetConfigActivity
import com.athar.app.widget.WidgetContent
import com.athar.app.widget.WidgetPinner
import com.athar.app.widget.WidgetStore
import com.athar.app.widget.WidgetType
import com.athar.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.first

/** Placed widgets (edit), add a widget, and the lock-screen card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetsSettingsCard(onMessage: (String) -> Unit, onOpenGallery: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as AtharApplication).container
    val lifecycle = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val placed = remember(refresh) {
        val store = WidgetStore(context)
        WidgetUpdater.placedWidgetIds(context).map { it to store.get(it) }
    }
    var managing by remember { mutableStateOf(false) }
    val pinRequested = stringResource(R.string.pin_requested)
    val pinUnsupported = stringResource(R.string.pin_unsupported)

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
        CardHeader(stringResource(R.string.widgets_title), Icons.Rounded.Widgets, tint = AtharColors.Violet)
        Spacer(Modifier.height(8.dp))
        Text(
            if (placed.isEmpty()) stringResource(R.string.widgets_none) else stringResource(R.string.widgets_placed, placed.size.toString()),
            style = MaterialTheme.typography.bodyMedium,
            color = AtharColors.TextSecondary,
        )
        placed.forEach { (id, config) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(context, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    config?.let { "${it.content.type.emoji}  " + stringResource(it.content.type.label) }
                        ?: stringResource(R.string.widget_tap_to_setup),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = AtharColors.Gold)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassButton(
            stringResource(R.string.widgets_add),
            onOpenGallery,
            color = AtharColors.Violet,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        GlassButton(
            stringResource(R.string.widgets_manage),
            { managing = true },
            color = AtharColors.Violet,
            filled = false,
            icon = Icons.Rounded.Tune,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.widgets_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
    if (managing) WidgetManagerSheet(onDismiss = { managing = false })

    Spacer(Modifier.height(12.dp))

    LockCardSettings()
}

/** The lock-screen card: what it shows, and the permissions it needs to appear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockCardSettings() {
    val context = LocalContext.current
    val container = (context.applicationContext as AtharApplication).container
    val lifecycle = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumed++ } }
    val canPost = remember(resumed) { Notifier.canPost(context) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumed++ }

    val lockCard by container.settings.lockCard.collectAsState()
    val items by produceState(LockItems()) {
        value = LockItems(
            counters = container.counterRepository.counters.first().map { it.id to it.title },
            habits = container.habitRepository.habits.first().map { it.id to it.title },
            people = container.debtRepository.people.first().map { it.id to it.name },
        )
    }
    val lockTypes = listOf(
        WidgetType.NextPrayer, WidgetType.Schedule, WidgetType.Salary, WidgetType.TodayTasks, WidgetType.Habits,
        WidgetType.Debts, WidgetType.Date, WidgetType.YearDots, WidgetType.Verse, WidgetType.Dhikr, WidgetType.Tasbih,
    )
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
        CardHeader(stringResource(R.string.lock_card_title), Icons.Rounded.Lock)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_card_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        if (!canPost) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.lock_card_need_permission), style = MaterialTheme.typography.bodyMedium, color = AtharColors.Rose)
            Spacer(Modifier.height(6.dp))
            GlassButton(
                stringResource(R.string.allow_notifications),
                {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                color = AtharColors.Rose,
                icon = Icons.Rounded.NotificationsActive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        val choices: List<Pair<String, WidgetContent?>> = buildList {
            add(stringResource(R.string.off) to null)
            lockTypes.forEach { type -> add("${type.emoji} " + stringResource(type.label) to WidgetContent(type)) }
            items.counters.forEach { (id, title) -> add("⏳ $title" to WidgetContent(WidgetType.Counter, id)) }
            items.habits.forEach { (id, title) -> add("🔥 $title" to WidgetContent(WidgetType.Habit, id)) }
            items.people.forEach { (id, name) -> add("👤 $name" to WidgetContent(WidgetType.DebtPerson, id)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (label, content) ->
                val selected = if (content == null) lockCard == null else lockCard?.type == content.type && lockCard?.itemId == content.itemId
                GlassChip(
                    label,
                    if (selected) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { container.settings.setLockCard(content) },
                )
            }
        }
        if (lockCard != null) {
            Spacer(Modifier.height(10.dp))
            GlassButton(
                stringResource(R.string.lock_card_settings),
                { runCatching { context.startActivity(LockCard.channelSettingsIntent(context)) } },
                filled = false,
                icon = Icons.Rounded.Settings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_card_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
}

private data class LockItems(
    val counters: List<Pair<Long, String>> = emptyList(),
    val habits: List<Pair<Long, String>> = emptyList(),
    val people: List<Pair<Long, String>> = emptyList(),
)
