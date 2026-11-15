package com.athar.app.feature.settings

import android.appwidget.AppWidgetManager
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
        Text(stringResource(R.string.widgets_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }

    Spacer(Modifier.height(12.dp))

    // Lock-screen card
    val lockCard by container.settings.lockCard.collectAsState()
    val counters by produceState(emptyList<Pair<Long, String>>()) {
        value = container.counterRepository.counters.first().map { it.id to it.title }
    }
    val lockTypes = listOf(
        WidgetType.NextPrayer, WidgetType.Salary, WidgetType.Schedule, WidgetType.TodayTasks, WidgetType.Habits,
        WidgetType.Date, WidgetType.Verse, WidgetType.Tasbih,
    )
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
        CardHeader(stringResource(R.string.lock_card_title), Icons.Rounded.Lock)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_card_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(
                stringResource(R.string.off),
                if (lockCard == null) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { container.settings.setLockCard(null) },
            )
            lockTypes.forEach { type ->
                GlassChip(
                    "${type.emoji} " + stringResource(type.label),
                    if (lockCard?.type == type) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { container.settings.setLockCard(WidgetContent(type)) },
                )
            }
            counters.forEach { (id, title) ->
                GlassChip(
                    "⏳ $title",
                    if (lockCard?.type == WidgetType.Counter && lockCard?.itemId == id) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { container.settings.setLockCard(WidgetContent(WidgetType.Counter, id)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_card_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
}
