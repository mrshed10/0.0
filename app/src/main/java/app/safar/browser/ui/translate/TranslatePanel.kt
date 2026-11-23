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
import app.safar.browser.data.OverlayStyle
import app.safar.browser.data.TranslateDisplayMode
import app.safar.browser.ui.settings.TextInputDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

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
            var keyFor by remember { mutableStateOf<ProviderId?>(null) }
            var keysVersion by remember { mutableIntStateOf(0) }
            fun hasKey(id: ProviderId): Boolean { keysVersion; return keyName(id)?.let { vm.keys.has(it) } ?: true }
            ProviderPicker(t.provider, hasKey = ::hasKey) { p ->
                if (p.needsKey && !hasKey(p)) keyFor = p // مدفوع بدون مفتاح ⇒ اطلب المفتاح أولاً
                else vm.updateTranslate { it.copy(provider = p) }
            }
            Text(
                when {
                    t.provider == ProviderId.AUTO -> "التلقائي يختار الأنسب: الذكاء الاصطناعي للمانغا والحوارات (إن وُجد مفتاح)، " +
                        "وإلا ترجمة Google المجانية، ثم ML Kit على الجهاز كبديل أخير يعمل بلا إنترنت."
                    t.provider == ProviderId.GOOGLE_FREE -> "مجانية وبدون مفتاح وجودة جيدة. ليست واجهة رسمية؛ عند تقييدها نلجأ تلقائياً إلى ML Kit."
                    t.provider == ProviderId.ON_DEVICE -> "مجانية وخاصة تماماً وتعمل بلا إنترنت، لكن جودتها أقل. تنزّل نموذج اللغة (~30MB) في أول مرة."
                    else -> "يتطلب مفتاح API. عند أي خطأ ننتقل تلقائياً إلى الخيارات المجانية."
                },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            Label("عرض الترجمة فوق الصور")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OverlayStyle.entries.forEachIndexed { i, st ->
                    SegmentedButton(
                        selected = t.overlayStyle == st,
                        onClick = { vm.updateTranslate { it.copy(overlayStyle = st) } },
                        shape = SegmentedButtonDefaults.itemShape(i, OverlayStyle.entries.size),
                    ) { Text(st.labelAr, maxLines = 1) }
                }
            }
            Text(
                when (t.overlayStyle) {
                    OverlayStyle.CLEAN -> "يمسح النص الأصلي بلون الفقاعة نفسه ويكتب الترجمة داخلها. على الرسم أو الخلفيات المنقطة يتحول تلقائياً إلى نص فقط حتى لا يشوّه الرسم."
                    OverlayStyle.TRANSLUCENT -> "خلفية بلون الفقاعة بعتامة قابلة للتعديل، بلا ضبابية."
                    OverlayStyle.TEXT_ONLY -> "الترجمة فقط مع حافة خفيفة جداً للوضوح، بدون أي صندوق."
                },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("عتامة الخلفية ${t.overlayOpacity}%", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = t.overlayOpacity.toFloat(), valueRange = 0f..35f, steps = 6,
                    onValueChange = { v -> vm.updateTranslate { it.copy(overlayOpacity = v.roundToInt()) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("إظهار الترجمات", modifier = Modifier.weight(1f))
                Switch(checked = t.overlaysVisible, onCheckedChange = { v -> vm.updateTranslate { it.copy(overlaysVisible = v) } })
            }
            Spacer(Modifier.height(24.dp))

            keyFor?.let { p ->
                TextInputDialog(
                    title = "مفتاح ${p.labelAr}",
                    password = true,
                    hint = "هذا المحرك يتطلب مفتاح API. يُحفظ مشفّراً على جهازك فقط.",
                    onDismiss = { keyFor = null },
                    onConfirm = { v ->
                        keyName(p)?.let { vm.keys.put(it, v) }
                        keysVersion++
                        if (v.isNotBlank()) vm.updateTranslate { it.copy(provider = p) }
                        keyFor = null
                    },
                )
            }
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

private fun keyName(id: ProviderId): SecureKeyStore.Name? = when (id) {
    ProviderId.GOOGLE -> SecureKeyStore.Name.GOOGLE
    ProviderId.DEEPL -> SecureKeyStore.Name.DEEPL
    ProviderId.CLAUDE -> SecureKeyStore.Name.CLAUDE
    ProviderId.GEMINI -> SecureKeyStore.Name.GEMINI
    else -> null
}

/** قائمة المزودين مقسومة بوضوح: ما يعمل بدون مفتاح، وما يتطلب مفتاح API. */
@Composable
fun ProviderPicker(current: ProviderId, hasKey: (ProviderId) -> Boolean, onPick: (ProviderId) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current.labelAr, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("تعمل بدون مفتاح", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            ProviderId.entries.filterNot { it.needsKey }.forEach { p ->
                DropdownMenuItem(
                    text = { Column { Text(p.labelAr); Text("✓ مجاني — يعمل الآن", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32)) } },
                    onClick = { onPick(p); open = false },
                )
            }
            HorizontalDivider()
            Text("تتطلب مفتاح API", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            ProviderId.entries.filter { it.needsKey }.forEach { p ->
                val ok = hasKey(p)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.labelAr)
                            Text(
                                if (ok) "✓ المفتاح محفوظ" else "🔑 يتطلب مفتاحاً — المس لإضافته",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    onClick = { onPick(p); open = false },
                )
            }
        }
    }
}
