package app.safar.browser.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * أدوات متجر Chrome الإلكتروني (دوال نقية قابلة للاختبار).
 *
 * الحقيقة التقنية: GeckoView المستقر يقبل فقط الإضافات الموقّعة من Mozilla، ولا يشغّل ملفات CRX مباشرة.
 * لذلك: نقرأ حزمة CRX من Google ⇒ نحلل manifest.json ⇒ نقيّم التوافق ⇒ نبحث عن النسخة الموقّعة نفسها
 * على متجر Firefox (AMO) ونثبّتها من هناك.
 */
object ChromeStore {
    const val HOST = "chromewebstore.google.com"
    const val HOME_URL = "https://chromewebstore.google.com/?utm_source=app-launcher&authuser=0&pli=1"

    private val idRegex = Regex("""^[a-p]{32}$""")

    fun isStoreUrl(url: String?): Boolean {
        val host = runCatching { java.net.URI(url ?: return false).host }.getOrNull() ?: return false
        return host == HOST || host == "chrome.google.com"
    }

    /** يستخرج معرّف الإضافة (32 حرفاً a-p) من رابط صفحة الإضافة في المتجر الجديد أو القديم. */
    fun extensionId(url: String?): String? {
        if (!isStoreUrl(url)) return null
        val path = runCatching { java.net.URI(url!!).path }.getOrNull() ?: return null
        return path.split('/').firstOrNull { idRegex.matches(it) }
    }

    /** رابط تنزيل حزمة CRX مباشرة من خوادم تحديث Chrome. */
    fun crxDownloadUrl(id: String, chromeVersion: String = "130.0.0.0") =
        "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=$chromeVersion" +
            "&acceptformat=crx2,crx3&x=id%3D$id%26uc"

    /** موضع بداية أرشيف ZIP داخل ملف CRX (الإصداران 2 و3). */
    fun zipOffset(crx: ByteArray): Int {
        require(crx.size > 16) { "ملف قصير جداً" }
        if (crx[0] == 'P'.code.toByte() && crx[1] == 'K'.code.toByte()) return 0 // ZIP عادي
        require(String(crx, 0, 4, Charsets.US_ASCII) == "Cr24") { "ليس ملف CRX" }
        val bb = ByteBuffer.wrap(crx).order(ByteOrder.LITTLE_ENDIAN)
        return when (val version = bb.getInt(4)) {
            2 -> 16 + bb.getInt(8) + bb.getInt(12)
            3 -> 12 + bb.getInt(8)
            else -> throw IllegalArgumentException("إصدار CRX غير مدعوم: $version")
        }
    }

    /** يقرأ manifest.json من الحزمة. */
    fun readManifest(crx: ByteArray): JsonObject {
        val off = zipOffset(crx)
        ZipInputStream(ByteArrayInputStream(crx, off, crx.size - off)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "manifest.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
                    return Json { isLenient = true }.parseToJsonElement(stripJsonComments(text)).jsonObject
                }
            }
        }
        throw IllegalArgumentException("لا يوجد manifest.json في الحزمة")
    }

    /** بعض ملفات manifest تحتوي تعليقات // (يقبلها Chrome) */
    fun stripJsonComments(s: String): String = s.lines().joinToString("\n") { line ->
        var inStr = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"' && (i == 0 || line[i - 1] != '\\')) inStr = !inStr
            if (!inStr && c == '/' && i + 1 < line.length && line[i + 1] == '/') return@joinToString line.substring(0, i)
            i++
        }
        line
    }

    enum class Level(val labelAr: String) {
        COMPATIBLE("متوافقة"),
        PARTIAL("متوافقة جزئياً"),
        INCOMPATIBLE("غير متوافقة"),
    }

    data class Compat(
        val name: String,
        val version: String,
        val manifestVersion: Int,
        val level: Level,
        val issues: List<String>,
    )

    /** أذونات Chrome غير الموجودة في Firefox/GeckoView على أندرويد. */
    private val unsupported = mapOf(
        "offscreen" to "المستندات الخفية (offscreen)",
        "sidePanel" to "اللوحة الجانبية",
        "tabGroups" to "مجموعات التبويبات",
        "declarativeContent" to "declarativeContent",
        "debugger" to "أداة التصحيح",
        "platformKeys" to "مفاتيح المنصة",
        "enterprise.platformKeys" to "مفاتيح المؤسسة",
        "documentScan" to "مسح المستندات",
        "fileBrowserHandler" to "معالج الملفات",
        "ttsEngine" to "محرك نطق",
        "gcm" to "Google Cloud Messaging",
        "power" to "إدارة الطاقة",
        "system.cpu" to "معلومات النظام", "system.memory" to "معلومات النظام", "system.display" to "معلومات النظام",
        "wallpaper" to "الخلفية", "readingList" to "قائمة القراءة", "userScripts" to "userScripts",
    )

    /** أذونات مدعومة في Firefox المكتبي لكنها محدودة/غير متاحة في GeckoView على أندرويد. */
    private val limitedOnAndroid = mapOf(
        "bookmarks" to "الإشارات المرجعية", "history" to "السجل", "sessions" to "الجلسات",
        "contextMenus" to "قوائم السياق", "commands" to "اختصارات لوحة المفاتيح",
        "topSites" to "المواقع الأكثر زيارة", "downloads" to "التنزيلات (محدود)",
        "identity" to "تسجيل الدخول", "tts" to "النطق", "pageCapture" to "حفظ الصفحة",
    )

    fun analyze(manifest: JsonObject): Compat {
        fun str(k: String) = (manifest[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val mv = (manifest["manifest_version"] as? JsonPrimitive)?.intOrNull ?: 2
        val perms = listOf("permissions", "optional_permissions").flatMap { k ->
            (manifest[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }
        val issues = mutableListOf<String>()
        var hard = 0
        perms.forEach { p ->
            unsupported[p]?.let { issues += "غير مدعوم: $it"; hard++ }
            limitedOnAndroid[p]?.let { issues += "محدود على أندرويد: $it" }
        }
        val bg = manifest["background"] as? JsonObject
        if (bg?.containsKey("service_worker") == true && bg["scripts"] == null) {
            issues += "تعتمد على Service Worker فقط (Firefox يحتاج background.scripts)"
            hard++
        }
        if (manifest["side_panel"] != null) { issues += "غير مدعوم: اللوحة الجانبية"; hard++ }
        if (manifest["chrome_url_overrides"] != null) issues += "استبدال صفحات المتصفح غير مدعوم على أندرويد"
        val level = when {
            hard > 0 -> Level.INCOMPATIBLE
            issues.isNotEmpty() -> Level.PARTIAL
            else -> Level.COMPATIBLE
        }
        return Compat(str("name"), str("version"), mv, level, issues)
    }

    /** الاسم قد يكون مفتاح ترجمة (__MSG_appName__) ⇒ نستخدم عنوان الصفحة بدلاً منه. */
    fun displayName(manifestName: String, pageTitle: String?): String {
        if (!manifestName.startsWith("__MSG_")) return manifestName
        return pageTitle?.substringBefore(" - Chrome Web Store")?.substringBefore(" – ")?.trim()?.takeIf { it.isNotBlank() } ?: manifestName
    }
}
