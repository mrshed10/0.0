package app.safar.browser.ui.extensions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val installed by vm.extensions.installed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(title = { Text("الإضافات") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        label = { Text("رابط ملف .xpi أو صفحة الإضافة على AMO") }, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = url.isNotBlank(), onClick = { vm.installExtension(normalizeAmo(url.trim())); url = "" }) { Text("تثبيت") }
                }
            }
            item { Section("المثبتة") }
            if (installed.isEmpty()) item { Text("لا توجد إضافات مثبتة", Modifier.padding(16.dp)) }
            items(installed, key = { it.id }) { ext ->
                val pinned = ext.id in settings.pinnedExtensions
                ListItem(
                    leadingContent = {
                        val icon = ext.icon
                        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(32.dp)) else Icon(Icons.Filled.Extension, null)
                    },
                    headlineContent = { Text(ext.name) },
                    supportingContent = { Text("الإصدار ${ext.version}" + if (ext.actionTitle != null) " • لها زر في الشريط" else "") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.togglePin(ext.id) }, enabled = ext.actionTitle != null) {
                                Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "تثبيت في الشريط")
                            }
                            Switch(checked = ext.enabled, onCheckedChange = { on -> scope.launch { vm.extensions.setEnabled(ext.id, on) } })
                            if (!ext.builtIn) IconButton(onClick = { vm.uninstallExtension(ext.id) }) { Icon(Icons.Filled.Delete, "إزالة") }
                        }
                    },
                )
            }
            item { Section("مقترحة") }
            items(vm.extensions.recommended, key = { it.amoSlug }) { rec ->
                val isInstalled = installed.any { it.name.equals(rec.name, ignoreCase = true) }
                ListItem(
                    headlineContent = { Text(rec.name) },
                    supportingContent = { Text(rec.descriptionAr) },
                    trailingContent = {
                        FilledTonalButton(enabled = !isInstalled, onClick = { vm.installExtension(rec.xpiUrl) }) {
                            Text(if (isInstalled) "مثبتة" else "تثبيت")
                        }
                    },
                )
            }
            item {
                Text(
                    "تعمل إضافات Firefox (WebExtensions) المتوافقة مع Android. الإضافات تعمل داخل محرك Gecko بصلاحيات محدودة بما توافق عليه عند التثبيت.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))

/** يحوّل رابط صفحة AMO (addons.mozilla.org/.../addon/<slug>/) إلى رابط تنزيل xpi. */
fun normalizeAmo(input: String): String {
    val m = Regex("""addons\.mozilla\.org/(?:[\w-]+/)?(?:firefox|android)/addon/([^/?#]+)""").find(input)
    return if (m != null && !input.endsWith(".xpi")) "https://addons.mozilla.org/firefox/downloads/latest/${m.groupValues[1]}/latest.xpi" else input
}
