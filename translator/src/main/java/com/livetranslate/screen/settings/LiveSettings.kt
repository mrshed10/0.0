package com.livetranslate.screen.settings

import android.content.Context
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.ContentMode
import com.livetranslate.screen.core.DisplayMode
import com.livetranslate.screen.core.OcrScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveConfig(
    /** "auto" = تعرّف تلقائي على لغة كل كتلة بشكل مستقل. */
    val sourceLang: String = "auto",
    val targetLang: String = "ar",
    /** اللغة المستخدمة عند عكس الاتجاه (النص العربي ← هذه اللغة). */
    val secondaryLang: String = "en",
    val bidirectional: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.FULL_SCREEN,
    val contentMode: ContentMode = ContentMode.TEXT,
    /** اتجاه قراءة المانغا: من اليمين لليسار (يابانية) أو العكس. */
    val mangaRtl: Boolean = true,
    /** محرك OCR عند اختيار "تلقائي" كلغة مصدر؛ null = اكتشاف نظام الكتابة تلقائياً. */
    val ocrScript: OcrScript? = null,
    val wifiOnlyDownloads: Boolean = false,
    val overlayOpacity: Float = 0.94f,
    val excludeSystemUi: Boolean = true,
    /** الشريط العائم مطويّ إلى دائرة صغيرة. */
    val barCollapsed: Boolean = false,
    /** في وضع النص: استخدم OCR تلقائياً عندما لا تحتوي الشاشة على نص قابل للقراءة (صور). */
    val ocrFallbackInTextMode: Boolean = false,
    /** موضع المربع العائم بإحداثيات الشاشة (فارغ = الافتراضي). */
    val bubble: Box = Box(0, 0, 0, 0),
) {
    /** null = يُكتشف تلقائياً من الصورة. */
    val effectiveOcrScript: OcrScript?
        get() = OcrScript.forLanguage(sourceLang) ?: ocrScript

    val needsProjection: Boolean
        get() = contentMode.usesOcr || ocrFallbackInTextMode
}

/** إعدادات مستمرة (SharedPreferences) مع StateFlow يُراقبه كل من الواجهة والخدمة. */
class LiveSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("live_settings", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<LiveConfig> = _config.asStateFlow()

    @Synchronized
    fun update(transform: (LiveConfig) -> LiveConfig) {
        val next = transform(_config.value)
        if (next == _config.value) return
        save(next)
        _config.value = next
    }

    private fun load(): LiveConfig {
        val d = LiveConfig()
        return LiveConfig(
            sourceLang = prefs.getString("source", d.sourceLang)!!,
            targetLang = prefs.getString("target", d.targetLang)!!,
            secondaryLang = prefs.getString("secondary", d.secondaryLang)!!,
            bidirectional = prefs.getBoolean("bidi", d.bidirectional),
            displayMode = enumOr(prefs.getString("display", null), d.displayMode),
            contentMode = enumOr(prefs.getString("content", null), d.contentMode),
            mangaRtl = prefs.getBoolean("manga_rtl", d.mangaRtl),
            ocrScript = prefs.getString("ocr_script_v2", null)?.let { n -> OcrScript.entries.firstOrNull { it.name == n } },
            wifiOnlyDownloads = prefs.getBoolean("wifi_only_v2", d.wifiOnlyDownloads),
            overlayOpacity = prefs.getFloat("opacity", d.overlayOpacity),
            excludeSystemUi = prefs.getBoolean("exclude_sysui", d.excludeSystemUi),
            barCollapsed = prefs.getBoolean("bar_collapsed", d.barCollapsed),
            ocrFallbackInTextMode = prefs.getBoolean("ocr_fallback", d.ocrFallbackInTextMode),
            bubble = Box(
                prefs.getInt("b_l", 0), prefs.getInt("b_t", 0),
                prefs.getInt("b_r", 0), prefs.getInt("b_b", 0),
            ),
        )
    }

    private fun save(c: LiveConfig) {
        prefs.edit()
            .putString("source", c.sourceLang)
            .putString("target", c.targetLang)
            .putString("secondary", c.secondaryLang)
            .putBoolean("bidi", c.bidirectional)
            .putString("display", c.displayMode.name)
            .putString("content", c.contentMode.name)
            .putBoolean("manga_rtl", c.mangaRtl)
            .putString("ocr_script_v2", c.ocrScript?.name ?: "AUTO")
            .putBoolean("wifi_only_v2", c.wifiOnlyDownloads)
            .putFloat("opacity", c.overlayOpacity)
            .putBoolean("exclude_sysui", c.excludeSystemUi)
            .putBoolean("bar_collapsed", c.barCollapsed)
            .putBoolean("ocr_fallback", c.ocrFallbackInTextMode)
            .putInt("b_l", c.bubble.left).putInt("b_t", c.bubble.top)
            .putInt("b_r", c.bubble.right).putInt("b_b", c.bubble.bottom)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        @Volatile private var instance: LiveSettings? = null
        fun get(context: Context): LiveSettings =
            instance ?: synchronized(this) {
                instance ?: LiveSettings(context.applicationContext).also { instance = it }
            }
    }
}

/** حالة الخدمة الحية التي تعرضها الواجهة الرئيسية والإشعار. */
data class ServiceStatus(
    val running: Boolean = false,
    val paused: Boolean = false,
    val projectionActive: Boolean = false,
    val message: String? = null,
    val downloadingPacks: Set<String> = emptySet(),
    /** تشخيص آخر دورة: عدد النصوص المكتشفة والمترجمة، وآخر خطأ. */
    val lastFound: Int = -1,
    val lastTranslated: Int = -1,
    val lastError: String? = null,
    /** اللغة المكتشفة تلقائياً في آخر دورة (تُعرض في الشريط العائم). */
    val detectedLang: String? = null,
)

object LiveState {
    val status = MutableStateFlow(ServiceStatus())
    fun update(transform: (ServiceStatus) -> ServiceStatus) {
        status.value = transform(status.value)
    }
}
