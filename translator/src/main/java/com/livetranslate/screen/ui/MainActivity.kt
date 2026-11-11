package com.livetranslate.screen.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.livetranslate.screen.core.ContentMode
import com.livetranslate.screen.core.DisplayMode
import com.livetranslate.screen.core.OcrScript
import com.livetranslate.screen.service.LiveTranslationService
import com.livetranslate.screen.service.ScreenTextAccessibilityService
import com.livetranslate.screen.settings.LiveConfig
import com.livetranslate.screen.settings.LiveSettings
import com.livetranslate.screen.settings.LiveState
import com.livetranslate.screen.translate.LanguagePacks
import com.livetranslate.screen.translate.TranslationSelfTest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF8AB4FF)) else lightColorScheme(primary = Color(0xFF2F6BFF))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot(this, resumeTick.intValue)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private data class Permissions(val overlay: Boolean, val accessibility: Boolean, val notifications: Boolean, val battery: Boolean)

private fun readPermissions(a: Activity) = Permissions(
    overlay = Settings.canDrawOverlays(a),
    accessibility = ScreenTextAccessibilityService.isEnabled(a),
    notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    battery = a.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(a.packageName),
)

@android.annotation.SuppressLint("BatteryLife")
private fun requestBatteryExemption(a: Activity) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}"))
    runCatching { a.startActivity(direct) }.onFailure {
        runCatching { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
private fun AppRoot(activity: Activity, resumeTick: Int) {
    var showPacks by remember { mutableStateOf(false) }
    BackHandler(enabled = showPacks) { showPacks = false }
    if (showPacks) PacksScreen(activity, onBack = { showPacks = false })
    else HomeScreen(activity, resumeTick, onOpenPacks = { showPacks = true })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(activity: Activity, resumeTick: Int, onOpenPacks: () -> Unit) {
    val settings = remember { LiveSettings.get(activity) }
    val cfg by settings.config.collectAsState()
    val status by LiveState.status.collectAsState()
    var perms by remember { mutableStateOf(readPermissions(activity)) }
    LaunchedEffect(resumeTick, status.running) { perms = readPermissions(activity) }
    var showA11yDisclosure by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf<String?>(null) } // "source" | "target" | "secondary"

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = readPermissions(activity)
    }
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            LiveTranslationService.start(activity, r.resultCode, data)
        } else {
            // بدون التقاط الشاشة: نبدأ بوضع النص المعتمد على شجرة الواجهة
            settings.update { it.copy(contentMode = ContentMode.TEXT, ocrFallbackInTextMode = false) }
            LiveTranslationService.start(activity)
        }
    }

    fun startService() {
        if (!perms.overlay) { openOverlaySettings(activity); return }
        if (cfg.needsProjection) projectionLauncher.launch(ProjectionPermissionActivity.captureIntent(activity))
        else LiveTranslationService.start(activity)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Translate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("الترجمة الحية للشاشة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("ترجمة مستمرة وتلقائية على الجهاز", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ——— الحالة والتشغيل ———
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        !status.running -> "الخدمة متوقفة"
                        status.paused -> "متوقفة مؤقتاً"
                        else -> "تعمل الآن — تتحدث تلقائياً عند التنقل بين التطبيقات والصفحات"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                if (status.running) {
                    Text(
                        "التقاط الشاشة: " + if (status.projectionActive) "نشط" else "غير مفعّل",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (status.running && cfg.sourceLang == "auto" && status.detectedLang != null) {
                    Text(
                        "اللغة المكتشفة: ${LanguagePacks.displayName(status.detectedLang!!)} ← ${LanguagePacks.displayName(cfg.targetLang)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (status.downloadingPacks.isNotEmpty()) {
                    Text("جارٍ تنزيل حزم اللغات: " + status.downloadingPacks.joinToString("، "), style = MaterialTheme.typography.bodySmall)
                }
                status.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (status.running) {
                    Button(
                        onClick = { LiveTranslationService.stop(activity) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("إيقاف الترجمة") }
                } else {
                    Button(onClick = ::startService, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("تشغيل الترجمة الحية")
                    }
                }
            }
        }

        // ——— الأذونات ———
        Section("الأذونات") {
            PermissionRow("الظهور فوق التطبيقات (المربع العائم والطبقة)", perms.overlay) { openOverlaySettings(activity) }
            PermissionRow("خدمة إمكانية الوصول (قراءة النصوص فورياً)", perms.accessibility) { showA11yDisclosure = true }
            PermissionRow("الإشعارات (التحكم من الإشعار الدائم)", perms.notifications) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("استثناء من تحسين البطارية (حتى لا يوقف النظام الخدمة)", perms.battery) { requestBatteryExemption(activity) }
            if (Build.MANUFACTURER.equals("HONOR", true) || Build.MANUFACTURER.equals("HUAWEI", true)) {
                Text(
                    "أجهزة هونر (MagicOS): افتح الإعدادات ← البطارية ← تشغيل التطبيقات ← \"الترجمة الحية للشاشة\" " +
                        "وأوقف \"الإدارة التلقائية\" ثم فعّل الخيارات الثلاثة. وإذا تعطلت خدمة إمكانية الوصول بعد تحديث أعد تفعيلها.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "يُطلب إذن التقاط الشاشة عند استخدام وضع الفيديو أو المانغا فقط.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ——— أوضاع الترجمة ———
        Section("وضع العرض") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("الشاشة الكاملة المستمرة", cfg.displayMode == DisplayMode.FULL_SCREEN) {
                    settings.update { it.copy(displayMode = DisplayMode.FULL_SCREEN) }
                }
                ChoiceChip("المربع الزجاجي العائم", cfg.displayMode == DisplayMode.BUBBLE) {
                    settings.update { it.copy(displayMode = DisplayMode.BUBBLE) }
                }
            }
            Text(
                if (cfg.displayMode == DisplayMode.BUBBLE)
                    "اسحب الشريط لتحريك المربع، واسحب المقبض الدائري في الزاوية لتغيير حجمه. كل ما تحته يُترجم مباشرة."
                else "تُترجم كل النصوص الظاهرة وتُغطّى بطبقة ترجمة تتحدث تلقائياً مع التمرير والتنقل.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("نوع المحتوى (المبدّل السريع)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("نصوص", cfg.contentMode == ContentMode.TEXT) { settings.update { it.copy(contentMode = ContentMode.TEXT) } }
                ChoiceChip("فيديو", cfg.contentMode == ContentMode.VIDEO) { settings.update { it.copy(contentMode = ContentMode.VIDEO) } }
                ChoiceChip("مانغا / صور", cfg.contentMode == ContentMode.MANGA) { settings.update { it.copy(contentMode = ContentMode.MANGA) } }
            }
            Text(
                when (cfg.contentMode) {
                    ContentMode.TEXT -> "قراءة مباشرة لنصوص الواجهة — أسرع استجابة وأقل استهلاك."
                    ContentMode.VIDEO -> "يكتشف لغة الترجمات المرئية تلقائياً ويترجمها للعربية في شريط لا يغطي الفيديو."
                    ContentMode.MANGA -> "يكتشف اللغة تلقائياً (ياباني/صيني/كوري/إنجليزي…)، يجمع الفقاعات ويرتب القراءة ويلوّن الترجمة بلون الفقاعة."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (cfg.contentMode == ContentMode.MANGA) {
                SwitchRow("اتجاه القراءة من اليمين لليسار (مانغا يابانية)", cfg.mangaRtl) { v -> settings.update { it.copy(mangaRtl = v) } }
                SwitchRow("إظهار أرقام ترتيب الفقاعات", cfg.showOrderBadges) { v -> settings.update { it.copy(showOrderBadges = v) } }
            }
            if (cfg.contentMode.usesOcr && cfg.sourceLang == "auto") {
                Text("نظام كتابة النص في الصورة:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("تلقائي (موصى به)", cfg.ocrScript == null) { settings.update { it.copy(ocrScript = null) } }
                    OcrScript.entries.forEach { s ->
                        ChoiceChip(scriptName(s), cfg.ocrScript == s) { settings.update { it.copy(ocrScript = s) } }
                    }
                }
            }
        }

        // ——— اللغات ———
        Section("اللغات واتجاه الترجمة") {
            LanguageRow("من", cfg.sourceLang) { pickLanguage = "source" }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    settings.update { c ->
                        if (c.sourceLang == "auto") c.copy(targetLang = c.secondaryLang, secondaryLang = c.targetLang)
                        else c.copy(sourceLang = c.targetLang, targetLang = c.sourceLang)
                    }
                }) { Icon(Icons.Filled.SwapHoriz, "عكس الاتجاه") }
            }
            LanguageRow("إلى", cfg.targetLang) { pickLanguage = "target" }
            SwitchRow("اتجاه ثنائي تلقائي (النص المكتوب بلغة الهدف يُترجم إلى اللغة الثانوية)", cfg.bidirectional) { v ->
                settings.update { it.copy(bidirectional = v) }
            }
            if (cfg.bidirectional) LanguageRow("اللغة الثانوية", cfg.secondaryLang) { pickLanguage = "secondary" }
            OutlinedButton(onClick = onOpenPacks, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("إدارة حزم اللغات (دون إنترنت)")
            }
        }

        // ——— التشخيص ———
        DiagnosticsSection(cfg.targetLang, status, perms.accessibility)

        // ——— متقدم ———
        Section("إعدادات متقدمة") {
            Text("شفافية طبقة الترجمة: ${(cfg.overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(value = cfg.overlayOpacity, onValueChange = { v -> settings.update { it.copy(overlayOpacity = v) } }, valueRange = 0.5f..1f)
            SwitchRow("تجاهل شريط الحالة والإشعارات", cfg.excludeSystemUi) { v -> settings.update { it.copy(excludeSystemUi = v) } }
            SwitchRow("OCR تلقائي للصور في وضع النصوص", cfg.ocrFallbackInTextMode) { v -> settings.update { it.copy(ocrFallbackInTextMode = v) } }
            SwitchRow("تنزيل حزم اللغات عبر Wi-Fi فقط", cfg.wifiOnlyDownloads) { v -> settings.update { it.copy(wifiOnlyDownloads = v) } }
        }
        Text(
            "كل المعالجة تتم على الجهاز: لا تُرسل نصوص الشاشة إلى أي خادم. يُستخدم الإنترنت فقط لتنزيل حزم اللغات.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }

    if (showA11yDisclosure) {
        AlertDialog(
            onDismissRequest = { showA11yDisclosure = false },
            title = { Text("إفصاح استخدام إمكانية الوصول") },
            text = {
                Text(
                    "يستخدم التطبيق خدمة إمكانية الوصول لقراءة النصوص الظاهرة على الشاشة فقط من أجل ترجمتها فورياً على جهازك " +
                        "وعرض الترجمة فوقها. لا تُجمع حقول الإدخال أو كلمات المرور، ولا تُرسل أي بيانات خارج الجهاز.\n\n" +
                        "في الشاشة التالية اختر \"الترجمة الحية للشاشة\" ثم فعّلها.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showA11yDisclosure = false
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("موافق، افتح الإعدادات") }
            },
            dismissButton = { TextButton(onClick = { showA11yDisclosure = false }) { Text("ليس الآن") } },
        )
    }

    pickLanguage?.let { which ->
        LanguagePickerDialog(
            includeAuto = which == "source",
            onDismiss = { pickLanguage = null },
            onPick = { code ->
                settings.update {
                    when (which) {
                        "source" -> it.copy(sourceLang = code)
                        "target" -> it.copy(targetLang = code)
                        else -> it.copy(secondaryLang = code)
                    }
                }
                pickLanguage = null
            },
        )
    }
}

private fun openOverlaySettings(a: Activity) {
    a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
}

private fun scriptName(s: OcrScript) = when (s) {
    OcrScript.LATIN -> "لاتيني"
    OcrScript.JAPANESE -> "ياباني"
    OcrScript.CHINESE -> "صيني"
    OcrScript.KOREAN -> "كوري"
    OcrScript.DEVANAGARI -> "ديفاناغاري"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) FilledTonalButton(onClick = onGrant) { Text("منح") }
    }
}

@Composable
private fun LanguageRow(label: String, code: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ", fontWeight = FontWeight.SemiBold)
        Text(LanguagePacks.displayName(code), Modifier.weight(1f))
    }
}

@Composable
private fun LanguagePickerDialog(includeAuto: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val langs = remember(includeAuto) { (if (includeAuto) listOf("auto") else emptyList()) + LanguagePacks.allLanguages }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        title = { Text("اختر اللغة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(langs) { code ->
                    Text(
                        LanguagePacks.displayName(code),
                        Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
        },
    )
}

@Composable
private fun PacksScreen(activity: Activity, onBack: () -> Unit) {
    val settings = remember { LiveSettings.get(activity) }
    val cfg by settings.config.collectAsState()
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { downloaded = LanguagePacks.downloaded() }

    fun act(code: String, block: suspend () -> Unit) {
        busy = busy + code
        scope.launch {
            block()
            downloaded = LanguagePacks.downloaded()
            busy = busy - code
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text("حزم اللغات دون إنترنت", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "كل حزمة ~30 ميغابايت. تُنزّل الحزم اللازمة تلقائياً عند أول استخدام" +
                if (cfg.wifiOnlyDownloads) " (عبر Wi-Fi فقط)." else ".",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val d = downloaded
        if (d == null) {
            CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
            return@Column
        }
        val pinned = listOf(cfg.targetLang, cfg.secondaryLang, cfg.sourceLang).filter { it != "auto" }.distinct()
        val ordered = pinned + LanguagePacks.allLanguages.filter { it !in pinned }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(ordered) { code ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(LanguagePacks.displayName(code), fontWeight = if (code in pinned) FontWeight.Bold else FontWeight.Normal)
                        Text(if (code in d) "مُنزّلة" else "غير مُنزّلة", style = MaterialTheme.typography.bodySmall)
                    }
                    when {
                        code in busy -> CircularProgressIndicator(Modifier.size(24.dp))
                        code == "en" -> Text("أساسية", style = MaterialTheme.typography.bodySmall)
                        code in d -> IconButton(onClick = { act(code) { LanguagePacks.delete(code) } }) { Icon(Icons.Filled.Delete, "حذف") }
                        else -> IconButton(onClick = { act(code) { LanguagePacks.download(code, cfg.wifiOnlyDownloads) } }) {
                            Icon(Icons.Filled.CloudDownload, "تنزيل")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(target: String, status: com.livetranslate.screen.settings.ServiceStatus, a11yEnabled: Boolean) {
    val scope = rememberCoroutineScope()
    val a11yConnected by ScreenTextAccessibilityService.connected.collectAsState()
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Section("التشخيص (إذا لم تظهر الترجمة)") {
        Text("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall)
        Text(
            "خدمة إمكانية الوصول: " + when {
                a11yConnected -> "✅ متصلة وتعمل"
                a11yEnabled -> "⚠️ مفعّلة في الإعدادات لكنها لا تعمل — أوقفها وأعد تفعيلها"
                else -> "❌ غير مفعّلة"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (status.running && status.lastFound >= 0) {
            Text(
                "آخر دورة: ${status.lastFound} نص مكتشف • ${status.lastTranslated} مترجم",
                style = MaterialTheme.typography.bodySmall,
            )
            if (status.lastFound == 0) Text(
                "لم يُعثر على نصوص: تأكد أن خدمة إمكانية الوصول متصلة، أو استخدم وضع المانغا/الصور للتطبيقات التي تعرض النص كصورة.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (status.lastFound > 0 && status.lastTranslated == 0) Text(
                "النصوص مكتشفة لكن لم تُترجم: غالباً حزمة اللغة لم تُنزّل بعد، أو النص بلغة الهدف نفسها. جرّب زر الاختبار.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        status.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            enabled = !testing,
            onClick = {
                testing = true
                testResult = "جارٍ الاختبار (قد يستغرق دقيقة عند أول تنزيل للحزمة)…"
                scope.launch {
                    testResult = TranslationSelfTest.run(target)
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("اختبار محرك الترجمة") }
        testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
