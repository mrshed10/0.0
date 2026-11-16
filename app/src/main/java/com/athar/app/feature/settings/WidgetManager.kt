package com.athar.app.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.widget.AtharSmallWidgetReceiver
import com.athar.app.widget.AtharWidgetReceiver
import com.athar.app.widget.GalleryTab
import com.athar.app.widget.WidgetProviders
import com.athar.app.widget.WidgetTemplates

/**
 * Which widgets the app offers: entries in the phone's widget list (launcher
 * picker) and designs in the in-app gallery. Each can be removed or kept.
 */
object WidgetEntries {
    /** Picker entries the user may switch off (the two custom entries stay: the gallery places widgets through them). */
    fun toggleable(context: Context): List<ComponentName> = WidgetProviders.receivers
        .filter { it != AtharWidgetReceiver::class.java && it != AtharSmallWidgetReceiver::class.java }
        .map { ComponentName(context, it) }

    fun isEnabled(context: Context, component: ComponentName): Boolean =
        context.packageManager.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setEnabled(context: Context, component: ComponentName, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    @Suppress("DEPRECATION")
    fun label(context: Context, component: ComponentName): String = runCatching {
        val pm = context.packageManager
        pm.getReceiverInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).loadLabel(pm).toString()
    }.getOrDefault(component.shortClassName)
}

@Composable
fun WidgetManagerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as AtharApplication).container.settings
    val hidden by settings.hiddenWidgets.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { WidgetEntries.toggleable(context).map { Triple(it, WidgetEntries.label(context, it), WidgetEntries.isEnabled(context, it)) } }
    val removedNote = stringResource(R.string.widgets_entry_removed)

    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.widgets_manage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.widgets_manage_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)

        FieldLabel(stringResource(R.string.widgets_phone_list))
        entries.forEach { (component, label, enabled) ->
            ToggleRow(label, enabled) { on ->
                runCatching { WidgetEntries.setEnabled(context, component, on) }
                if (!on) Toast.makeText(context, removedNote, Toast.LENGTH_LONG).show()
                version++
            }
        }
        Text(stringResource(R.string.widgets_phone_list_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)

        FieldLabel(stringResource(R.string.widgets_gallery_list))
        GalleryTab.entries.filter { it != GalleryTab.New }.forEach { tab ->
            val templates = WidgetTemplates.all.filter { it.tab == tab }
            if (templates.isNotEmpty()) {
                Column {
                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
                    templates.forEach { t ->
                        ToggleRow(stringResource(t.name), t.id !in hidden) { on -> settings.setWidgetHidden(t.id, !on) }
                    }
                }
            }
        }
        if (hidden.isNotEmpty() || entries.any { !it.third }) {
            GlassButton(
                stringResource(R.string.widgets_show_all),
                {
                    settings.showAllWidgets()
                    entries.forEach { runCatching { WidgetEntries.setEnabled(context, it.first, true) } }
                    version++
                },
                filled = false,
                icon = Icons.Rounded.Visibility,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) AtharColors.TextPrimary else AtharColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Violet),
        )
    }
}
