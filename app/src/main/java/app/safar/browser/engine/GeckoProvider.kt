package app.safar.browser.engine

import android.content.Context
import app.safar.browser.BuildConfig
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.SecureDnsMode
import app.safar.browser.data.dohUri
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * GeckoRuntime واحد فقط لكل عملية. هنا تُطبَّق إعدادات الخصوصية الأصلية للمحرك:
 * - HTTPS-Only (مع صفحة خطأ أصلية فيها زر "المتابعة عبر HTTP" = استثناء لكل موقع)
 * - حماية التتبع المحسّنة (Strict) + حظر الإعلانات/المعدّنات/البصمة
 * - سياسة الكوكيز الخارجية
 * - حماية البصمة (FPP) التي تشمل عشوائية Canvas
 */
object GeckoProvider {
    @Volatile private var runtime: GeckoRuntime? = null

    fun get(context: Context, settings: BrowserSettings): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: create(context.applicationContext, settings).also { runtime = it }
        }

    private fun create(context: Context, s: BrowserSettings): GeckoRuntime {
        val builder = GeckoRuntimeSettings.Builder()
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .aboutConfigEnabled(BuildConfig.DEBUG)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(antiTracking(s))
                    .enhancedTrackingProtectionLevel(etpLevel(s))
                    .cookieBehavior(cookieBehavior(s))
                    .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .strictSocialTrackingProtection(s.adBlock)
                    .build()
            )
            .allowInsecureConnections(insecure(s))
        // FPP لا يتوفر في Builder؛ يُضبط على إعدادات الـ Runtime بعد إنشائه (داخل apply)
        return GeckoRuntime.create(context, builder.build()).also { apply(it, s) }
    }

    /** يُستدعى عند تغيّر الإعدادات أثناء التشغيل. */
    fun apply(runtime: GeckoRuntime, s: BrowserSettings) {
        val cb = runtime.settings.contentBlocking
        cb.setAntiTracking(antiTracking(s))
        cb.setEnhancedTrackingProtectionLevel(etpLevel(s))
        cb.setCookieBehavior(cookieBehavior(s))
        cb.setStrictSocialTrackingProtection(s.adBlock)
        runtime.settings.setAllowInsecureConnections(insecure(s))
        runtime.settings.setFingerprintingProtection(s.blockCanvas) // GV-API
        // فرض تفعيل التكبير: تجاوز user-scalable=no / maximum-scale في المواقع
        runtime.settings.setForceUserScalableEnabled(s.forceEnableZoom)
        applySecureDns(runtime, s)
    }

    /**
     * DNS الآمن (DNS over HTTPS) عبر TRR في Gecko:
     *  - تلقائي: DoH أولاً مع الرجوع لـ DNS الشبكة عند الفشل (TRR_MODE_FIRST)
     *  - مقدم محدد: DoH فقط (TRR_MODE_ONLY) — لا تسرب لطلبات DNS عادية
     */
    private fun applySecureDns(runtime: GeckoRuntime, s: BrowserSettings) {
        val uri = s.dohUri()
        val rs = runtime.settings
        if (uri == null) {
            rs.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF) // GV-API
            return
        }
        rs.setTrustedRecursiveResolverUri(uri)
        rs.setTrustedRecursiveResolverMode(
            if (s.secureDns == SecureDnsMode.PROVIDER) GeckoRuntimeSettings.TRR_MODE_ONLY else GeckoRuntimeSettings.TRR_MODE_FIRST
        )
    }

    private fun antiTracking(s: BrowserSettings): Int =
        if (s.adBlock) {
            ContentBlocking.AntiTracking.STRICT or
                ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
        } else {
            ContentBlocking.AntiTracking.DEFAULT
        }

    private fun etpLevel(s: BrowserSettings) =
        if (s.adBlock) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT

    private fun cookieBehavior(s: BrowserSettings) =
        if (s.blockThirdPartyCookies) ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        // حتى عند السماح: عزل الكوكيز الخارجية لكل موقع (Total Cookie Protection)
        else ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS

    private fun insecure(s: BrowserSettings) =
        if (s.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL
}
