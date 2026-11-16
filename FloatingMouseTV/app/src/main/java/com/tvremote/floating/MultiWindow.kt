package com.tvremote.floating

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/**
 * فتح التطبيقات ومجموعات التطبيقات، وتقسيم الشاشة / النوافذ المتعددة.
 *
 * ملاحظة صريحة: أندرويد لا يوفّر للتطبيقات العادية أمراً رسمياً لوضع تطبيقين جنباً إلى جنب.
 * لذلك نستخدم أفضل الطرق المتاحة بالترتيب، ونرجع للفتح المتتالي إن لم يدعمها الجهاز:
 *  1) تقسيم الشاشة: أمر إمكانية الوصول GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN ثم فتح الثاني
 *     بـ FLAG_ACTIVITY_LAUNCH_ADJACENT.
 *  2) النوافذ العائمة (Freeform): ActivityOptions.setLaunchBounds لكل تطبيق في نصف الشاشة،
 *     مع محاولة setLaunchWindowingMode (دالة مخفية في أندرويد، قد يرفضها النظام).
 */
object MultiWindow {

    private const val TAG = "MultiWindow"
    private const val WINDOWING_MODE_FREEFORM = 5
    private const val STEP_MS = 1500L

    private const val KEY_FORCE_RESIZABLE = "force_resizable_activities"
    private const val KEY_FREEFORM = "enable_freeform_support"

    private val handler = Handler(Looper.getMainLooper())

    // ───────────────────────── قدرات الجهاز ─────────────────────────

    private fun systemBool(name: String): Boolean? {
        val id = Resources.getSystem().getIdentifier(name, "bool", "android")
        return if (id == 0) null else try {
            Resources.getSystem().getBoolean(id)
        } catch (e: Exception) {
            null
        }
    }

    private fun globalInt(context: Context, key: String): Int =
        Settings.Global.getInt(context.contentResolver, key, 0)

    /** هل نظام الجهاز يدعم تقسيم الشاشة؟ */
    fun splitSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val multi = systemBool("config_supportsMultiWindow") ?: true
        val split = systemBool("config_supportsSplitScreenMultiWindow") ?: true
        val forced = globalInt(context, KEY_FORCE_RESIZABLE) != 0
        return (multi || forced) && split
    }

    /** هل نظام الجهاز يدعم النوافذ العائمة (Freeform)؟ */
    fun freeformSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            globalInt(context, KEY_FREEFORM) != 0
    }

    fun developerFlagsEnabled(context: Context): Boolean =
        globalInt(context, KEY_FORCE_RESIZABLE) != 0 && globalInt(context, KEY_FREEFORM) != 0

    /**
     * يفعّل خياري المطوّر "فرض قابلية تغيير حجم الأنشطة" و"تفعيل النوافذ الحرة".
     * يحتاج WRITE_SECURE_SETTINGS (تُمنح عبر ADB) ثم إعادة تشغيل الجهاز.
     */
    fun enableDeveloperFlags(context: Context): Boolean = try {
        Settings.Global.putInt(context.contentResolver, KEY_FORCE_RESIZABLE, 1)
        Settings.Global.putInt(context.contentResolver, KEY_FREEFORM, 1)
        true
    } catch (e: Exception) {
        Log.w(TAG, "enableDeveloperFlags failed", e)
        false
    }

    // ───────────────────────── تقسيم الشاشة ─────────────────────────

    /** دخول/خروج وضع تقسيم الشاشة للتطبيق الحالي */
    fun toggleSplit(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toast(context, R.string.split_not_supported)
            return false
        }
        val service = ControlAccessibilityService.instance
        if (service == null) {
            toast(context, R.string.need_accessibility)
            return false
        }
        @Suppress("DEPRECATION")
        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        if (!ok) toast(context, R.string.split_not_supported)
        return ok
    }

    // ───────────────────────── فتح التطبيقات ─────────────────────────

    fun launchApp(
        context: Context,
        pkg: String,
        options: ActivityOptions? = null,
        adjacent: Boolean = false
    ): Boolean {
        val intent = AppRepository.launchIntent(context, pkg)
        if (intent == null) {
            toast(context, R.string.app_not_found)
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (adjacent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }
        return try {
            context.startActivity(intent, options?.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "launch $pkg failed", e)
            toast(context, R.string.app_not_found)
            false
        }
    }

    /** فتح مجموعة تطبيقات بالطريقة المختارة (مع الرجوع لطريقة مدعومة تلقائياً) */
    fun launchGroup(context: Context, group: AppGroup) {
        val app = context.applicationContext
        val pkgs = group.packages.filter { AppRepository.isInstalled(app, it) }
        if (pkgs.isEmpty()) {
            toast(app, R.string.app_not_found)
            return
        }
        val mode = resolveMode(app, group.mode, pkgs.size)
        if (mode != group.mode) toast(app, R.string.mode_fallback)

        when (mode) {
            LaunchMode.SEQUENTIAL -> pkgs.forEachIndexed { i, pkg ->
                handler.postDelayed({ launchApp(app, pkg) }, i * STEP_MS)
            }

            LaunchMode.SPLIT -> {
                // التطبيقات الزائدة (إن وُجدت) تُفتح أولاً في الخلفية، ثم آخر تطبيقين في تقسيم الشاشة
                val extra = pkgs.dropLast(2)
                val first = pkgs[pkgs.size - 2]
                val second = pkgs[pkgs.size - 1]
                extra.forEachIndexed { i, pkg ->
                    handler.postDelayed({ launchApp(app, pkg) }, i * STEP_MS)
                }
                val t = extra.size * STEP_MS
                handler.postDelayed({ launchApp(app, first) }, t)
                handler.postDelayed({ toggleSplit(app) }, t + STEP_MS)
                handler.postDelayed({ launchApp(app, second, adjacent = true) }, t + 2 * STEP_MS)
            }

            LaunchMode.WINDOWS -> {
                val tiles = tiles(app, pkgs.size)
                pkgs.forEachIndexed { i, pkg ->
                    handler.postDelayed({
                        launchApp(app, pkg, options = freeformOptions(tiles[i]))
                    }, i * 900L)
                }
            }
        }
    }

    /** إذا كانت الطريقة المختارة غير مدعومة نختار أفضل بديل */
    private fun resolveMode(context: Context, wanted: LaunchMode, count: Int): LaunchMode {
        if (count < 2) return LaunchMode.SEQUENTIAL
        val split = splitSupported(context) && ControlAccessibilityService.instance != null
        val free = freeformSupported(context)
        return when (wanted) {
            LaunchMode.SEQUENTIAL -> LaunchMode.SEQUENTIAL
            LaunchMode.SPLIT -> when {
                split -> LaunchMode.SPLIT
                free -> LaunchMode.WINDOWS
                else -> LaunchMode.SEQUENTIAL
            }
            LaunchMode.WINDOWS -> when {
                free -> LaunchMode.WINDOWS
                split -> LaunchMode.SPLIT
                else -> LaunchMode.SEQUENTIAL
            }
        }
    }

    /** تقسيم الشاشة إلى نصفين (تطبيقان) أو أربعة أرباع (3-4 تطبيقات) */
    private fun tiles(context: Context, count: Int): List<Rect> {
        val w = context.resources.displayMetrics.widthPixels
        val h = context.resources.displayMetrics.heightPixels
        return if (count <= 2) {
            listOf(Rect(0, 0, w / 2, h), Rect(w / 2, 0, w, h))
        } else {
            listOf(
                Rect(0, 0, w / 2, h / 2), Rect(w / 2, 0, w, h / 2),
                Rect(0, h / 2, w / 2, h), Rect(w / 2, h / 2, w, h)
            )
        }
    }

    private fun freeformOptions(bounds: Rect): ActivityOptions? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        // setLaunchWindowingMode دالة مخفية في أندرويد: نحاول استدعاءها، وقد يرفضها النظام بأمان
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (e: Throwable) {
            Log.i(TAG, "setLaunchWindowingMode not available: ${e.javaClass.simpleName}")
        }
        return options
    }

    private fun toast(context: Context, res: Int) {
        handler.post { Toast.makeText(context, res, Toast.LENGTH_LONG).show() }
    }
}
