package app.safar.browser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

enum class AddressBarPosition { TOP, BOTTOM }

/** أزرار شريط الأدوات القابلة للتخصيص (الترتيب = ترتيب الظهور). */
enum class ToolbarItem(val labelAr: String) {
    BACK("رجوع"),
    FORWARD("تقدّم"),
    HOME("الرئيسية"),
    RELOAD("تحديث"),
    TABS_COUNTER("عداد التبويبات"),
    VERTICAL_TABS("التبويبات العمودية"),
    INCOGNITO("تصفح متخفٍ"),
    EXTENSIONS("الإضافات"),
    LIVE_TRANSLATE("الترجمة الحية"),
    BOOKMARKS("الإشارات المرجعية"),
    SHARE("مشاركة"),
    MENU("القائمة"),
}

enum class GestureAction(val labelAr: String) {
    SWITCH_TABS("تبديل التبويبات"),
    BACK_FORWARD("رجوع / تقدّم"),
    NONE("بدون"),
}

enum class TranslateDisplayMode(val labelAr: String) {
    CONTINUOUS("ترجمة مستمرة"),
    FLOATING("مربع زجاجي عائم"),
}

enum class ContentKind(val labelAr: String) {
    TEXT("نصوص"),
    MANGA("صور / مانغا"),
    VIDEO("فيديو"),
}

/** needsKey = يتطلب مفتاح API من المستخدم. */
enum class ProviderId(val labelAr: String, val needsKey: Boolean) {
    AUTO("تلقائي (ذكي)", false),
    GOOGLE_FREE("ترجمة Google المجانية", false),
    ON_DEVICE("على الجهاز (ML Kit، بلا إنترنت)", false),
    GOOGLE("Google Cloud Translation", true),
    DEEPL("DeepL API", true),
    CLAUDE("Claude (ذكاء اصطناعي)", true),
    GEMINI("Gemini (ذكاء اصطناعي)", true),
}

/** طريقة رسم الترجمة فوق الصور/المانغا. */
enum class OverlayStyle(val labelAr: String) {
    CLEAN("تنظيف الفقاعة"),
    TRANSLUCENT("خلفية شبه شفافة"),
    TEXT_ONLY("نص فقط"),
}

enum class OcrEngineId(val labelAr: String) {
    AUTO("تلقائي"),
    MLKIT("ML Kit"),
    TESSERACT("Tesseract"),
}

@Serializable
data class TranslateSettings(
    val enabled: Boolean = false,
    val mode: TranslateDisplayMode = TranslateDisplayMode.CONTINUOUS,
    val content: ContentKind = ContentKind.TEXT,
    val source: String = "auto",
    val target: String = "ar",
    val provider: ProviderId = ProviderId.AUTO,
    val ocr: OcrEngineId = OcrEngineId.AUTO,
    val claudeModel: String = "claude-opus-5-5",
    val geminiModel: String = "gemini-2.5-flash",
    /** تعليمات إضافية لنماذج الذكاء الاصطناعي (مسرد أسماء، أسلوب، ...). */
    val llmInstructions: String = "",
    /** نمط عرض الترجمة فوق الصور + نسبة عتامة الخلفية (0..35%). */
    val overlayStyle: OverlayStyle = OverlayStyle.CLEAN,
    val overlayOpacity: Int = 20,
    /** إظهار/إخفاء الترجمات بلمسة واحدة دون إيقاف الترجمة. */
    val overlaysVisible: Boolean = true,
)

val RTL_LANGS = setOf("ar", "fa", "he", "ur")

/** نظام أسماء النطاقات الآمن (DNS over HTTPS). */
enum class SecureDnsMode(val labelAr: String) {
    OFF("متوقف"),
    AUTOMATIC("استخدام مقدم الخدمة الحالي (تلقائي)"),
    PROVIDER("اختيار مقدم خدمة آخر"),
}

enum class DnsProvider(val labelAr: String, val uri: String) {
    CLOUDFLARE("Cloudflare (1.1.1.1)", "https://mozilla.cloudflare-dns.com/dns-query"),
    GOOGLE("Google (Public DNS)", "https://dns.google/dns-query"),
    CLEANBROWSING_FAMILY("CleanBrowsing (Family Filter)", "https://doh.cleanbrowsing.org/doh/family-filter/"),
    OPENDNS("OpenDNS", "https://doh.opendns.com/dns-query"),
    CUSTOM("مخصص", ""),
}

/** زر الاختصار السريع بجانب شريط العنوان (مثل Chrome). */
enum class QuickAction(val labelAr: String) {
    AUTO("حسب معدل استخدامك"),
    NEW_TAB("علامة تبويب جديدة"),
    SHARE("مشاركة هذه الصفحة"),
    VOICE_SEARCH("بحث صوتي"),
    TRANSLATE("ترجمة"),
    BOOKMARK("الإضافة إلى الإشارات المرجعية"),
    READ_ALOUD("الاستماع إلى هذه الصفحة"),
}

@Serializable
data class Shortcut(val title: String, val url: String)

/** تخصيص صفحة علامة التبويب الجديدة. */
@Serializable
data class NewTabSettings(
    val showShortcuts: Boolean = true,
    val showBookmarks: Boolean = true,
    val showRecent: Boolean = true,
    /** 0 = لون الثيم، وإلا مؤشر في NEW_TAB_BACKGROUNDS */
    val background: Int = 0,
    val shortcuts: List<Shortcut> = listOf(
        Shortcut("Wikipedia", "https://ar.wikipedia.org"),
        Shortcut("YouTube", "https://m.youtube.com"),
        Shortcut("GitHub", "https://github.com"),
    ),
)

val NEW_TAB_BACKGROUNDS = listOf(0x00000000, 0xFF1E3A5F.toInt(), 0xFF2E4A2E.toInt(), 0xFF4A2E4A.toInt(), 0xFF3A3A3A.toInt(), 0xFF5C3B1E.toInt())

@Serializable
data class BrowserSettings(
    val onboardingDone: Boolean = false,
    // عام
    val searchEngineId: String = "duckduckgo",
    val homePage: String = "about:blank",
    val restoreTabs: Boolean = true,
    // الواجهة والإيماءات
    val addressBar: AddressBarPosition = AddressBarPosition.BOTTOM,
    val toolbarItems: List<ToolbarItem> = listOf(
        ToolbarItem.BACK, ToolbarItem.LIVE_TRANSLATE, ToolbarItem.TABS_COUNTER,
        ToolbarItem.EXTENSIONS, ToolbarItem.MENU,
    ),
    val barSwipe: GestureAction = GestureAction.SWITCH_TABS,
    val swipeUpOpensTabs: Boolean = true,
    val pinnedExtensions: List<String> = emptyList(),
    // الخصوصية
    val adBlock: Boolean = true,
    val httpsOnly: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val hideLocalIp: Boolean = true,
    val blockCanvas: Boolean = true,
    /** مواقع مستثناة من الحماية (حظر التتبع/Canvas). */
    val siteExceptions: Set<String> = emptySet(),
    // التحميل والوسائط
    val backgroundAudio: Boolean = true,
    val mediaNotification: Boolean = true,
    val externalDownloader: Boolean = false,
    val externalDownloaderPackage: String = "",
    val downloadNotifications: Boolean = true,
    // الإضافات المثبتة من مصدر خارجي: id -> رابط xpi (لاستعادتها من النسخة الاحتياطية)
    val extensionSources: Map<String, String> = emptyMap(),
    // الترجمة
    val translate: TranslateSettings = TranslateSettings(),
    // DNS الآمن
    val secureDns: SecureDnsMode = SecureDnsMode.OFF,
    val dnsProvider: DnsProvider = DnsProvider.CLOUDFLARE,
    val customDnsUri: String = "",
    // زر الاختصار السريع
    val quickActionEnabled: Boolean = true,
    val quickAction: QuickAction = QuickAction.AUTO,
    /** عدّاد استخدام الإجراءات لاختيار "حسب معدل الاستخدام". */
    val actionUsage: Map<String, Int> = emptyMap(),
    // التكبير والتصغير
    val showZoomInMenu: Boolean = true,
    val forceEnableZoom: Boolean = false,
    /** مستوى التكبير المحفوظ لكل موقع (نسبة مئوية). */
    val siteZoom: Map<String, Int> = emptyMap(),
    // وضع سطح المكتب
    val desktopByDefault: Boolean = false,
    val desktopSites: Set<String> = emptySet(),
    val autoDesktopForWebStore: Boolean = true,
    // صفحة علامة التبويب الجديدة
    val newTab: NewTabSettings = NewTabSettings(),
)

/** عنوان DoH الفعلي حسب الإعدادات (null = بلا DoH). */
fun BrowserSettings.dohUri(): String? = when (secureDns) {
    SecureDnsMode.OFF -> null
    SecureDnsMode.AUTOMATIC -> DnsProvider.CLOUDFLARE.uri
    SecureDnsMode.PROVIDER -> if (dnsProvider == DnsProvider.CUSTOM) customDnsUri.trim().takeIf { isValidDohUri(it) } else dnsProvider.uri
}

fun isValidDohUri(u: String): Boolean =
    Regex("""^https://[A-Za-z0-9.-]+(:\d{1,5})?(/[^\s]*)?$""").matches(u.trim())

/** اختيار وظيفة الزر السريع: المحدد، أو الأكثر استخداماً في وضع "تلقائي". */
fun BrowserSettings.effectiveQuickAction(): QuickAction {
    if (quickAction != QuickAction.AUTO) return quickAction
    return actionUsage.filterKeys { k -> QuickAction.entries.any { it.name == k && it != QuickAction.AUTO } }
        .maxByOrNull { it.value }?.key?.let { QuickAction.valueOf(it) } ?: QuickAction.NEW_TAB
}

data class Language(val code: String, val nameAr: String)

val LANGUAGES = listOf(
    Language("ar", "العربية"), Language("en", "الإنجليزية"), Language("ja", "اليابانية"),
    Language("ko", "الكورية"), Language("zh", "الصينية"), Language("fr", "الفرنسية"),
    Language("de", "الألمانية"), Language("es", "الإسبانية"), Language("it", "الإيطالية"),
    Language("pt", "البرتغالية"), Language("ru", "الروسية"), Language("tr", "التركية"),
    Language("fa", "الفارسية"), Language("ur", "الأردية"), Language("hi", "الهندية"),
    Language("id", "الإندونيسية"), Language("vi", "الفيتنامية"), Language("th", "التايلاندية"),
    Language("uk", "الأوكرانية"), Language("nl", "الهولندية"), Language("pl", "البولندية"),
    Language("he", "العبرية"),
)
