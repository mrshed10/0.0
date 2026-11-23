package app.safar.browser.engine

import android.content.Context
import app.safar.browser.BuildConfig
import app.safar.browser.data.BrowserSettings
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
