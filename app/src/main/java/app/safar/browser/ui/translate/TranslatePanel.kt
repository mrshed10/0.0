package app.safar.browser.ui.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.ContentKind
import app.safar.browser.data.LANGUAGES
import app.safar.browser.data.ProviderId
import app.safar.browser.data.SecureKeyStore
import app.safar.browser.data.TranslateDisplayMode

/** لوحة التحكم السريعة بالترجمة الحية. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatePanel(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.translate.status.collectAsStateWithLifecycle()
    val t = settings.translate

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الترجمة الحية", style = MaterialTheme.typography.titleLarge)
                    val sub = when {
                        status.error != null -> "خطأ: ${status.error}"
                        status.lastProvider != null -> "آخر ترجمة عبر ${status.lastProvider!!.labelAr}" +
                            (status.detectedSource?.let { " • المصدر: $it" } ?: "")
                        else -> "تعمل على كل الصفحات وتتحدث مع التمرير والتنقل"
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = t.enabled, onCheckedChange = { on -> vm.updateTranslate { it.copy(enabled = on) } })
            }

            Spacer(Modifier.height(12.dp))
            Label("طريقة العرض")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TranslateDisplayMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = t.mode == m,
                        onClick = { vm.updateTranslate { it.copy(mode = m, enabled = true) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TranslateDisplayMode.entries.size),
                    ) { Text(m.labelAr) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Label("نوع المحتوى")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentKind.entries.forEach { k ->
                    FilterChip(selected = t.content == k, onClick = { vm.updateTranslate { it.copy(content = k) } }, label = { Text(k.labelAr) })
                }
            }
            Text(
                when (t.content) {
                    ContentKind.TEXT -> "قراءة مباشرة لنص الصفحة: أسرع استجابة وأقل استهلاكاً."
                    ContentKind.MANGA -> "التقاط الشاشة بعد توقف التمرير ← تحسين الصورة ← OCR ← ترجمة فوق الفقاعات. المس المربع لرؤية الأصل."
                    ContentKind.VIDEO -> "يترجم ملفات الترجمة المرفقة بالفيديو، أو يقرأ الترجمة المحروقة في الصورة بـ OCR."
                },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            Label("اللغات")
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguagePicker(t.source, allowAuto = true, Modifier.weight(1f)) { c -> vm.updateTranslate { it.copy(source = c) } }
                IconButton(
                    enabled = t.source != "auto",
                    onClick = { vm.updateTranslate { it.copy(source = it.target, target = it.source) } },
                ) { Icon(Icons.AutoMirrored.Filled.CompareArrows, "تبديل") }
                LanguagePicker(t.target, allowAuto = false, Modifier.weight(1f)) { c -> vm.updateTranslate { it.copy(target = c) } }
            }

            Spacer(Modifier.height(12.dp))
            Label("مصدر الترجمة")
            ProviderPicker(t.provider, configured = { id ->
                when (id) {
                    ProviderId.AUTO, ProviderId.ON_DEVICE -> true
                    ProviderId.GOOGLE -> vm.keys.has(SecureKeyStore.Name.GOOGLE)
                    ProviderId.DEEPL -> vm.keys.has(SecureKeyStore.Name.DEEPL)
                    ProviderId.CLAUDE -> vm.keys.has(SecureKeyStore.Name.CLAUDE)
                    ProviderId.GEMINI -> vm.keys.has(SecureKeyStore.Name.GEMINI)
                }
            }) { p -> vm.updateTranslate { it.copy(provider = p) } }
            Text(
                "التلقائي: Google للقوائم والنصوص القصيرة، DeepL للفقرات، Claude/Gemini للحوارات والعامية والمانغا، " +
                    "وML Kit على الجهاز كبديل دائم. أضف المفاتيح من الإعدادات ← الترجمة.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))

@Composable
fun LanguagePicker(code: String, allowAuto: Boolean, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = if (code == "auto") "اكتشاف تلقائي" else LANGUAGES.firstOrNull { it.code == code }?.nameAr ?: code
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(name, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowAuto) DropdownMenuItem(text = { Text("اكتشاف تلقائي") }, onClick = { onPick("auto"); open = false })
            LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l.nameAr) }, onClick = { onPick(l.code); open = false }) }
        }
    }
}

@Composable
fun ProviderPicker(current: ProviderId, configured: (ProviderId) -> Boolean, onPick: (ProviderId) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current.labelAr, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ProviderId.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Row {
                            Text(p.labelAr)
                            Spacer(Modifier.width(8.dp))
                            if (!configured(p)) Text("(بدون مفتاح)", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onPick(p); open = false },
                )
            }
        }
    }
}
