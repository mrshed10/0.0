package com.tvtools.controlpanel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * كل الـ Intents الخاصة بشاشات النظام في مكان واحد.
 *
 * الشركات المصنّعة تحذف أو تعيد تسمية بعض الشاشات، لذلك كل وجهة لها قائمة بدائل
 * تُجرَّب بالترتيب حتى تنجح إحداها.
 */
object SystemIntents {

    private const val SETTINGS = "com.android.settings"
    private const val TV_SETTINGS = "com.android.tv.settings"

    /** مفتاح يجعل الإعدادات تقفز إلى خيار معيّن وتبرزه (مدعوم في AOSP). */
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

    fun component(pkg: String, cls: String): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(pkg, cls)

    /** إعدادات أندرويد الكاملة (غالبًا مخفية على التلفاز) ثم إعدادات التلفاز كبديل. */
    fun androidSettings(): List<Intent> = listOf(
        component(SETTINGS, "$SETTINGS.Settings"),
        component(SETTINGS, "$SETTINGS.homepage.SettingsHomepageActivity"),
        Intent(Settings.ACTION_SETTINGS),
        component(TV_SETTINGS, "$TV_SETTINGS.MainSettings"),
    )

    fun tvSettings(): List<Intent> = listOf(
        component(TV_SETTINGS, "$TV_SETTINGS.MainSettings"),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun developerOptions(): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        component(SETTINGS, "$SETTINGS.Settings\$DevelopmentSettingsDashboardActivity"),
        component(SETTINGS, "$SETTINGS.Settings\$DevelopmentSettingsActivity"),
        component(SETTINGS, "$SETTINGS.DevelopmentSettings"),
        component(TV_SETTINGS, "$TV_SETTINGS.system.development.DevelopmentActivity"),
    )

    /** خيارات المطور مع محاولة القفز مباشرة إلى خيار "تصحيح أخطاء USB". */
    fun usbDebugging(): List<Intent> =
        listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(EXTRA_FRAGMENT_ARG_KEY, "enable_adb"),
        ) + developerOptions()

    /** "حول الجهاز" — منها يُفعَّل وضع المطور بالضغط 7 مرات على "رقم الإصدار". */
    fun deviceInfo(): List<Intent> = listOf(
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        component(TV_SETTINGS, "$TV_SETTINGS.about.AboutActivity"),
        component(TV_SETTINGS, "$TV_SETTINGS.device.DeviceInfoActivity"),
    )

    fun action(action: String): List<Intent> = listOf(Intent(action))

    /** نتيجة محاولة فتح نشاط، مع سبب الفشل لعرض رسالة واضحة. */
    sealed class LaunchResult {
        object Ok : LaunchResult()
        /** النشاط غير موجود على هذا الجهاز (حذفته الشركة أو اسم خاطئ). */
        data class NotFound(val detail: String) : LaunchResult()
        /** النشاط داخلي (exported=false) أو محمي بصلاحية نظام. */
        data class Denied(val detail: String) : LaunchResult()
        data class Failed(val detail: String) : LaunchResult()
    }

    fun launch(context: Context, intent: Intent): LaunchResult {
        val i = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            LaunchResult.Ok
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NotFound(e.message.orEmpty())
        } catch (e: SecurityException) {
            LaunchResult.Denied(e.message.orEmpty())
        } catch (e: RuntimeException) {
            LaunchResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** يفتح النشاط، وعند الفشل يعرض Toast يشرح السبب. @return true عند النجاح. */
    fun launchWithFeedback(context: Context, intent: Intent): Boolean {
        val messageRes = when (launch(context, intent)) {
            LaunchResult.Ok -> return true
            is LaunchResult.NotFound -> R.string.error_activity_not_found
            is LaunchResult.Denied -> R.string.error_activity_denied
            is LaunchResult.Failed -> R.string.error_activity_failed
        }
        Toast.makeText(
            context,
            context.getString(messageRes, describe(intent)),
            Toast.LENGTH_LONG,
        ).show()
        return false
    }

    /**
     * يجرّب الـ Intents بالترتيب ويفتح أول واحد يعمل.
     * @param showError عرض رسالة "غير متوفرة" عند فشل كل البدائل.
     * @return true إذا نجح الفتح.
     */
    fun launchFirst(context: Context, candidates: List<Intent>, showError: Boolean = true): Boolean {
        for (intent in candidates) {
            if (launch(context, intent) == LaunchResult.Ok) return true
        }
        if (showError) {
            Toast.makeText(context, R.string.error_not_available, Toast.LENGTH_LONG).show()
        }
        return false
    }

    fun describe(intent: Intent): String =
        intent.component?.flattenToShortString() ?: intent.action.orEmpty()

    val manageUnknownSources: String?
        get() = if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES else null
}
