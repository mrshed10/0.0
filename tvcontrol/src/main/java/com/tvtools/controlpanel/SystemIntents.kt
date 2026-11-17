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

    /**
     * يجرّب الـ Intents بالترتيب ويفتح أول واحد يعمل.
     * @return true إذا نجح الفتح.
     */
    fun launchFirst(context: Context, candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            if (tryLaunch(context, intent) == null) return true
        }
        Toast.makeText(context, R.string.error_not_available, Toast.LENGTH_LONG).show()
        return false
    }

    /** @return null عند النجاح، أو رسالة الخطأ. */
    fun tryLaunch(context: Context, intent: Intent): String? {
        val i = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            null
        } catch (e: ActivityNotFoundException) {
            e.message ?: "ActivityNotFoundException"
        } catch (e: SecurityException) {
            e.message ?: "SecurityException"
        } catch (e: RuntimeException) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun describe(intent: Intent): String =
        intent.component?.flattenToShortString() ?: intent.action.orEmpty()

    val manageUnknownSources: String?
        get() = if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES else null
}
