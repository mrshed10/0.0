package com.onetap.assist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.onetap.assist.AssistLauncher
import com.onetap.assist.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable component: an app's launcher activity or a CREATE_SHORTCUT activity. */
class ComponentItem(val label: String, val component: ComponentName, val icon: ImageBitmap?)

private const val ICON_PX = 72

suspend fun loadComponents(context: Context, intent: Intent): List<ComponentItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            ComponentItem(
                label = ri.loadLabel(pm).toString(),
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                icon = runCatching { ri.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
            )
        }
        .distinctBy { it.component }
        .sortedBy { it.label.lowercase() }
}

/** Dialog listing components resolved for [intent]. */
@Composable
fun ComponentPickerDialog(
    title: String,
    intent: Intent,
    onPick: (ComponentItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val items by produceState<List<ComponentItem>?>(null, intent) { value = loadComponents(ctx, intent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val list = items
            when {
                list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.loading))
                }
                list.isEmpty() -> Text(stringResource(R.string.empty_list))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.component.flattenToString() }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp)) {
                                item.icon?.let { Image(it, contentDescription = null, Modifier.size(36.dp)) }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(item.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Free-form intent URI / deep link input, validated with Intent.parseUri. */
@Composable
fun CustomUriDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_uri_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    placeholder = { Text(stringResource(R.string.custom_uri_hint)) },
                    isError = error,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) {
                    Text(
                        stringResource(R.string.custom_uri_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank() && AssistLauncher.parseTarget(text) != null) onSave(text.trim()) else error = true
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
