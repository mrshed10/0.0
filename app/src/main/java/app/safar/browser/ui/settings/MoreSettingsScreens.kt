package app.safar.browser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.DnsProvider
import app.safar.browser.data.NEW_TAB_BACKGROUNDS
import app.safar.browser.data.SecureDnsMode
import app.safar.browser.data.Shortcut
import app.safar.browser.data.isValidDohUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad -> content(Modifier.padding(pad)) }
}

/** استخدام "نظام أسماء النطاقات" (DNS) الآمن — مطابق لشاشة Chrome. */
@Composable
fun DnsSettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var custom by remember(s.customDnsUri) { mutableStateOf(s.customDnsUri) }
    Screen("استخدام نظام أسماء النطاقات (DNS) الآمن", onBack) { m ->
        LazyColumn(m) {
            item {
                SwitchRow(
                    "استخدام \"نظام أسماء النطاقات\" (DNS) الآمن",
                    s.secureDns != SecureDnsMode.OFF,
                    "تحديد طريقة الاتصال بمواقع الويب من خلال اتصال آمن (DNS over HTTPS)",
                ) { on -> vm.updateSettings { it.copy(secureDns = if (on) SecureDnsMode.AUTOMATIC else SecureDnsMode.OFF) } }
            }
            if (s.secureDns != SecureDnsMode.OFF) {
                item {
                    Choice("استخدام مقدم الخدمة الحالي", "التلقائي: DoH أولاً مع الرجوع لـ DNS الشبكة إن لم يكن متاحاً.",
                        s.secureDns == SecureDnsMode.AUTOMATIC) { vm.updateSettings { it.copy(secureDns = SecureDnsMode.AUTOMATIC) } }
                    Choice("اختيار مقدم خدمة آخر", "اتصال آمن دائماً عبر المزود المختار (بلا رجوع لـ DNS عادي).",
                        s.secureDns == SecureDnsMode.PROVIDER) { vm.updateSettings { it.copy(secureDns = SecureDnsMode.PROVIDER) } }
                }
                if (s.secureDns == SecureDnsMode.PROVIDER) {
                    item {
                        Column(Modifier.padding(start = 32.dp)) {
                            DnsProvider.entries.forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().selectable(s.dnsProvider == p) { vm.updateSettings { it.copy(dnsProvider = p) } }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = s.dnsProvider == p, onClick = null)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(p.labelAr)
                                        if (p.uri.isNotEmpty()) Text(p.uri, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (s.dnsProvider == DnsProvider.CUSTOM) {
                                val ok = isValidDohUri(custom)
                                OutlinedTextField(
                                    value = custom, onValueChange = { custom = it }, singleLine = true,
                                    label = { Text("عنوان خادم DoH (https://…)") },
                                    isError = custom.isNotBlank() && !ok,
                                    supportingText = { if (custom.isNotBlank() && !ok) Text("أدخل عنواناً يبدأ بـ https://") },
                                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                                )
                                TextButton(enabled = ok, onClick = { vm.updateSettings { it.copy(customDnsUri = custom.trim()) } }) { Text("حفظ") }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "يُشفَّر الاتصال بين المتصفح وخادم الأسماء فلا تعرف الشبكة أو مزود الإنترنت المواقع التي تزورها. " +
                        "يُطبق فوراً على كل التبويبات.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Choice(title: String, sub: String, selected: Boolean, onClick: () -> Unit) =
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        modifier = Modifier.clickable(onClick = onClick),
    )

/** تخصيص علامة التبويب الجديدة: الأقسام، الخلفية، والاختصارات. */
@Composable
fun NewTabSettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val nt = s.newTab
    var adding by remember { mutableStateOf(false) }
    fun upd(f: (app.safar.browser.data.NewTabSettings) -> app.safar.browser.data.NewTabSettings) = vm.updateSettings { it.copy(newTab = f(it.newTab)) }
    Screen("تخصيص علامة تبويب جديدة", onBack) { m ->
        LazyColumn(m) {
            item { SwitchRow("الاختصارات", nt.showShortcuts, "اختصاراتك + المواقع الأكثر زيارة") { v -> upd { it.copy(showShortcuts = v) } } }
            item { SwitchRow("الإشارات المرجعية", nt.showBookmarks) { v -> upd { it.copy(showBookmarks = v) } } }
            item { SwitchRow("آخر الزيارات", nt.showRecent) { v -> upd { it.copy(showRecent = v) } } }
            item { SectionTitle("الخلفية") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NEW_TAB_BACKGROUNDS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                                .border(if (nt.background == i) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { upd { it.copy(background = i) } },
                        )
                    }
                }
            }
            item { SectionTitle("اختصاراتي") }
            nt.shortcuts.forEach { sc ->
                item(key = sc.url) {
                    ListItem(headlineContent = { Text(sc.title) }, supportingContent = { Text(sc.url) }, trailingContent = {
                        IconButton(onClick = { upd { it.copy(shortcuts = it.shortcuts - sc) } }) { Icon(Icons.Filled.Close, "حذف") }
                    })
                }
            }
            item { ClickRow("إضافة اختصار") { adding = true } }
        }
    }
    if (adding) TextInputDialog(
        title = "رابط الاختصار", hint = "مثال: https://example.com",
        onDismiss = { adding = false },
        onConfirm = { v ->
            val url = v.trim().let { if (it.startsWith("http")) it else "https://$it" }
            val title = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: url
            upd { it.copy(shortcuts = it.shortcuts + Shortcut(title, url)) }
            adding = false
        },
    )
}
