package app.safar.browser.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.AddressBarPosition
import app.safar.browser.data.GestureAction
import app.safar.browser.data.LANGUAGES
import app.safar.browser.data.OcrEngineId
import app.safar.browser.data.ProviderId
import app.safar.browser.data.SearchEngines
import app.safar.browser.data.SecureKeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Dialog {
    data class Key(val name: SecureKeyStore.Name, val title: String, val hint: String) : Dialog
    data class Text(val title: String, val initial: String, val hint: String? = null, val apply: (String) -> Unit) : Dialog
    data class BackupPassword(val export: Boolean, val uri: Uri) : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: BrowserViewModel,
    onBack: () -> Unit,
    openToolbar: () -> Unit,
    requestDefaultBrowser: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var keysVersion by remember { mutableIntStateOf(0) } // لإعادة رسم حالة المفاتيح بعد الحفظ

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { dialog = Dialog.BackupPassword(export = true, uri = it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { dialog = Dialog.BackupPassword(export = false, uri = it) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("الإعدادات") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            // ------------------------------------------------ عام
            item { SectionTitle("عام") }
            item {
                ChoiceRow("محرك البحث", SearchEngines.all, SearchEngines.byId(s.searchEngineId), { it.name }) { e ->
                    vm.updateSettings { it.copy(searchEngineId = e.id) }
                }
            }
            item {
                ClickRow("الصفحة الرئيسية", s.homePage) {
                    dialog = Dialog.Text("الصفحة الرئيسية", s.homePage) { v -> vm.updateSettings { it.copy(homePage = v.ifBlank { "about:blank" }) } }
                }
            }
            item { SwitchRow("استعادة التبويبات عند الفتح", s.restoreTabs) { v -> vm.updateSettings { it.copy(restoreTabs = v) } } }
            item { ClickRow("تعيين كمتصفح افتراضي", "لفتح الروابط من التطبيقات الأخرى في سفر", requestDefaultBrowser) }

            // ------------------------------------------------ الواجهة والإيماءات
            item { SectionTitle("الواجهة والإيماءات") }
            item {
                ChoiceRow("مكان شريط العنوان", AddressBarPosition.entries, s.addressBar, { if (it == AddressBarPosition.TOP) "أعلى" else "أسفل" }) { p ->
                    vm.updateSettings { it.copy(addressBar = p) }
                }
            }
            item { ClickRow("تخصيص أزرار شريط الأدوات", s.toolbarItems.joinToString("، ") { it.labelAr }, openToolbar) }
            item {
                ChoiceRow("السحب الأفقي على الشريط", GestureAction.entries, s.barSwipe, { it.labelAr }) { g ->
                    vm.updateSettings { it.copy(barSwipe = g) }
                }
            }
            item { SwitchRow("السحب للأعلى يفتح التبويبات", s.swipeUpOpensTabs) { v -> vm.updateSettings { it.copy(swipeUpOpensTabs = v) } } }

            // ------------------------------------------------ الخصوصية
            item { SectionTitle("الخصوصية والأمان") }
            item { SwitchRow("حظر الإعلانات والمتتبعات", s.adBlock, "حماية تتبع صارمة مدمجة في Gecko") { v -> vm.updateSettings { it.copy(adBlock = v) } } }
            item { SwitchRow("فرض اتصالات HTTPS", s.httpsOnly, "يمكن المتابعة عبر HTTP لموقع بعينه من صفحة التحذير") { v -> vm.updateSettings { it.copy(httpsOnly = v) } } }
            item { SwitchRow("حظر الكوكيز الخارجية", s.blockThirdPartyCookies, "عند الإيقاف تبقى معزولة لكل موقع") { v -> vm.updateSettings { it.copy(blockThirdPartyCookies = v) } } }
            item { SwitchRow("إخفاء عنوان الشبكة المحلي (WebRTC)", s.hideLocalIp) { v -> vm.updateSettings { it.copy(hideLocalIp = v) } } }
            item { SwitchRow("منع قراءة Canvas (البصمة الرقمية)", s.blockCanvas) { v -> vm.updateSettings { it.copy(blockCanvas = v) } } }
            item {
                ClickRow("إضافة موقع مستثنى", "${s.siteExceptions.size} موقع مستثنى") {
                    dialog = Dialog.Text("نطاق الموقع (مثال: example.com)", "") { v ->
                        val host = v.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                        if (host.isNotBlank()) vm.updateSettings { it.copy(siteExceptions = it.siteExceptions + host) }
                    }
                }
            }
            s.siteExceptions.sorted().forEach { host ->
                item(key = "ex-$host") {
                    ListItem(headlineContent = { Text(host) }, trailingContent = {
                        IconButton(onClick = { vm.updateSettings { it.copy(siteExceptions = it.siteExceptions - host) } }) { Icon(Icons.Filled.Close, "إزالة") }
                    })
                }
            }

            // ------------------------------------------------ الترجمة
            item { SectionTitle("الترجمة الحية") }
            item {
                ChoiceRow("اللغة الهدف الافتراضية", LANGUAGES, LANGUAGES.firstOrNull { it.code == s.translate.target } ?: LANGUAGES.first(), { it.nameAr }) { l ->
                    vm.updateTranslate { it.copy(target = l.code) }
                }
            }
            item { ChoiceRow("مصدر الترجمة", ProviderId.entries, s.translate.provider, { it.labelAr }) { p -> vm.updateTranslate { it.copy(provider = p) } } }
            item { ChoiceRow("محرك OCR", OcrEngineId.entries, s.translate.ocr, { it.labelAr }) { o -> vm.updateTranslate { it.copy(ocr = o) } } }
            item(key = "keys-$keysVersion") {
                val k = vm.keys
                ClickRow("مفتاح Google Cloud Translation", if (k.has(SecureKeyStore.Name.GOOGLE)) "محفوظ ✓" else "غير مضبوط") {
                    dialog = Dialog.Key(SecureKeyStore.Name.GOOGLE, "مفتاح Google Cloud", "من Google Cloud Console ← APIs ← Cloud Translation API")
                }
                ClickRow("مفتاح DeepL API", if (k.has(SecureKeyStore.Name.DEEPL)) "محفوظ ✓" else "غير مضبوط") {
                    dialog = Dialog.Key(SecureKeyStore.Name.DEEPL, "مفتاح DeepL", "المفاتيح المجانية تنتهي بـ :fx")
                }
                ClickRow("مفتاح Claude (Anthropic)", if (k.has(SecureKeyStore.Name.CLAUDE)) "محفوظ ✓" else "غير مضبوط") {
                    dialog = Dialog.Key(SecureKeyStore.Name.CLAUDE, "مفتاح Claude", "من console.anthropic.com ← API Keys")
                }
                ClickRow("مفتاح Gemini", if (k.has(SecureKeyStore.Name.GEMINI)) "محفوظ ✓" else "غير مضبوط") {
                    dialog = Dialog.Key(SecureKeyStore.Name.GEMINI, "مفتاح Gemini", "من Google AI Studio")
                }
            }
            item {
                ClickRow("نموذج Claude", s.translate.claudeModel) {
                    dialog = Dialog.Text("معرّف نموذج Claude", s.translate.claudeModel, "الافتراضي claude-opus-5-5. للأسرع والأرخص: claude-haiku-5-5") { v ->
                        vm.updateTranslate { it.copy(claudeModel = v.trim().ifBlank { "claude-opus-5-5" }) }
                    }
                }
            }
            item {
                ClickRow("نموذج Gemini", s.translate.geminiModel) {
                    dialog = Dialog.Text("معرّف نموذج Gemini", s.translate.geminiModel) { v ->
                        vm.updateTranslate { it.copy(geminiModel = v.trim().ifBlank { "gemini-2.5-flash" }) }
                    }
                }
            }
            item {
                ClickRow("تعليمات إضافية للذكاء الاصطناعي", s.translate.llmInstructions.ifBlank { "مسرد أسماء، أسلوب، لهجة..." }) {
                    dialog = Dialog.Text("تعليمات الترجمة", s.translate.llmInstructions, "مثال: ترجم إلى الفصحى المبسطة، واحتفظ بأسماء الشخصيات: Luffy=لوفي") { v ->
                        vm.updateTranslate { it.copy(llmInstructions = v) }
                    }
                }
            }

            // ------------------------------------------------ التحميل والوسائط
            item { SectionTitle("التحميل والوسائط") }
            item { SwitchRow("تشغيل الصوت في الخلفية", s.backgroundAudio, "يستمر عند تبديل التطبيقات أو قفل الشاشة") { v -> vm.updateSettings { it.copy(backgroundAudio = v) } } }
            item { SwitchRow("إشعار التحكم بالوسائط", s.mediaNotification) { v -> vm.updateSettings { it.copy(mediaNotification = v) } } }
            item { SwitchRow("إشعارات التنزيل", s.downloadNotifications) { v -> vm.updateSettings { it.copy(downloadNotifications = v) } } }
            item { SwitchRow("استخدام مدير تحميل خارجي", s.externalDownloader) { v -> vm.updateSettings { it.copy(externalDownloader = v) } } }
            if (s.externalDownloader) item {
                ClickRow("حزمة مدير التحميل (اختياري)", s.externalDownloaderPackage.ifBlank { "اختيار عند كل تنزيل" }) {
                    dialog = Dialog.Text("اسم الحزمة", s.externalDownloaderPackage, "مثال: com.dv.adm") { v ->
                        vm.updateSettings { it.copy(externalDownloaderPackage = v.trim()) }
                    }
                }
            }

            // ------------------------------------------------ النسخ الاحتياطي
            item { SectionTitle("النسخ الاحتياطي") }
            item {
                ClickRow("حفظ نسخة احتياطية مشفرة", "الإعدادات + الإشارات المرجعية + الجلسات + قائمة الإضافات (بدون مفاتيح API)") {
                    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    exportLauncher.launch("safar-backup-$date.sfbk")
                }
            }
            item { ClickRow("استعادة من نسخة احتياطية") { importLauncher.launch(arrayOf("*/*")) } }
            item { ListItem(headlineContent = { Text("سفر 0.1.0") }, supportingContent = { Text("محرك العرض: GeckoView") }) }
        }
    }

    when (val d = dialog) {
        is Dialog.Key -> TextInputDialog(
            title = d.title, password = true, hint = d.hint,
            onDismiss = { dialog = null },
            onConfirm = { v -> vm.keys.put(d.name, v); keysVersion++; dialog = null },
        )
        is Dialog.Text -> TextInputDialog(
            title = d.title, initial = d.initial, hint = d.hint,
            onDismiss = { dialog = null },
            onConfirm = { v -> d.apply(v); dialog = null },
        )
        is Dialog.BackupPassword -> TextInputDialog(
            title = if (d.export) "كلمة مرور التشفير" else "كلمة مرور النسخة",
            password = true,
            hint = if (d.export) "لا يمكن استعادة النسخة بدونها. استخدم 8 أحرف على الأقل." else null,
            onDismiss = { dialog = null },
            onConfirm = { v ->
                if (d.export && v.length < 8) return@TextInputDialog
                if (d.export) vm.exportBackup(d.uri, v.toCharArray()) else vm.importBackup(d.uri, v.toCharArray())
                dialog = null
            },
        )
        null -> Unit
    }
}
