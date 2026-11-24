package app.safar.browser.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.QuickAction
import app.safar.browser.data.ToolbarItem
import app.safar.browser.data.effectiveQuickAction
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton

private const val MAX_ITEMS = 7

/** اختيار أزرار الشريط وترتيبها. القائمة (MENU) إلزامية حتى لا يفقد المستخدم الوصول للإعدادات. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarCustomizeScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val enabled = s.toolbarItems
    val disabled = ToolbarItem.entries.filterNot { it in enabled }

    fun set(list: List<ToolbarItem>) = vm.updateSettings { it.copy(toolbarItems = list) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("أزرار شريط الأدوات") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            // اختصارات شريط الأدوات (الزر الذكي بجانب شريط العنوان) — مثل Chrome
            item { Text("اختصارات شريط الأدوات", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
            item {
                SwitchRow("اختصارات شريط الأدوات", s.quickActionEnabled, "زر سريع بجانب شريط العنوان لأكثر مهمة تستخدمها") { v ->
                    vm.updateSettings { it.copy(quickActionEnabled = v) }
                }
            }
            if (s.quickActionEnabled) {
                items(QuickAction.entries, key = { "qa-${it.name}" }) { qa ->
                    ListItem(
                        leadingContent = { RadioButton(selected = s.quickAction == qa, onClick = { vm.updateSettings { it.copy(quickAction = qa) } }) },
                        headlineContent = { Text(qa.labelAr) },
                        supportingContent = if (qa == QuickAction.AUTO) ({ Text("الاقتراح الحالي: ${s.effectiveQuickAction().labelAr}") }) else null,
                        modifier = Modifier.clickable { vm.updateSettings { it.copy(quickAction = qa) } },
                    )
                }
            }
            item {
                Text("المفعّلة (${enabled.size}/$MAX_ITEMS) — بالترتيب من البداية", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp))
            }
            items(enabled, key = { "on-${it.name}" }) { item ->
                val idx = enabled.indexOf(item)
                ListItem(
                    leadingContent = {
                        Checkbox(checked = true, enabled = item != ToolbarItem.MENU, onCheckedChange = { set(enabled - item) })
                    },
                    headlineContent = { Text(item.labelAr) },
                    trailingContent = {
                        Row {
                            IconButton(enabled = idx > 0, onClick = { set(enabled.toMutableList().apply { add(idx - 1, removeAt(idx)) }) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, "أعلى")
                            }
                            IconButton(enabled = idx < enabled.lastIndex, onClick = { set(enabled.toMutableList().apply { add(idx + 1, removeAt(idx)) }) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, "أسفل")
                            }
                        }
                    },
                )
            }
            item { Text("غير مفعّلة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
            items(disabled, key = { "off-${it.name}" }) { item ->
                ListItem(
                    leadingContent = {
                        Checkbox(checked = false, enabled = enabled.size < MAX_ITEMS, onCheckedChange = {
                            val menuIdx = enabled.indexOf(ToolbarItem.MENU)
                            set(if (menuIdx >= 0) enabled.toMutableList().apply { add(menuIdx, item) } else enabled + item)
                        })
                    },
                    headlineContent = { Text(item.labelAr) },
                )
            }
        }
    }
}
