package com.tvremote.floating

import android.content.Context
import android.content.SharedPreferences

/** كل إعدادات التطبيق محفوظة في SharedPreferences */
object Prefs {

    private const val NAME = "floating_prefs"

    const val SIZE_MIN_DP = 36
    const val SIZE_MAX_DP = 96
    const val ALPHA_MIN = 20
    const val ALPHA_MAX = 100

    private fun p(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** هل يريد المستخدم أن تعمل القائمة العائمة؟ */
    fun isFloatingEnabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setFloatingEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    /** حجم الأيقونة بالـ dp */
    fun bubbleSizeDp(c: Context) = p(c).getInt("size_dp", 56)
    fun setBubbleSizeDp(c: Context, v: Int) = p(c).edit().putInt("size_dp", v).apply()

    /** شفافية الأيقونة بالنسبة المئوية (100 = معتمة تماماً) */
    fun bubbleAlpha(c: Context) = p(c).getInt("alpha", 70)
    fun setBubbleAlpha(c: Context, v: Int) = p(c).edit().putInt("alpha", v).apply()

    /** آخر مكان للأيقونة على الشاشة (-1 = الافتراضي) */
    fun bubbleX(c: Context) = p(c).getInt("x", -1)
    fun bubbleY(c: Context) = p(c).getInt("y", -1)
    fun setBubblePosition(c: Context, x: Int, y: Int) =
        p(c).edit().putInt("x", x).putInt("y", y).apply()

    /** إظهار/إخفاء كل زر في القائمة العائمة */
    fun isActionVisible(c: Context, a: FloatAction) = p(c).getBoolean("show_" + a.name, true)
    fun setActionVisible(c: Context, a: FloatAction, v: Boolean) =
        p(c).edit().putBoolean("show_" + a.name, v).apply()

    /** الزر الأوسط للماوس = رجوع */
    fun isMiddleBackEnabled(c: Context) = p(c).getBoolean("middle_back", true)
    fun setMiddleBackEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("middle_back", v).apply()

    /** رمز الزر الذي يرسله الجهاز عند الضغط على الزر الأوسط (إن كان يرسله كزر) */
    fun middleKeyCode(c: Context) = p(c).getInt("middle_key", -1)
    fun setMiddleKeyCode(c: Context, v: Int) = p(c).edit().putInt("middle_key", v).apply()

    /** اختصارات التطبيقات المفضلة (بالترتيب) */
    fun shortcuts(c: Context): List<String> =
        (p(c).getString("shortcuts", "") ?: "").split(',').filter { it.isNotBlank() }

    fun setShortcuts(c: Context, list: List<String>) =
        p(c).edit().putString("shortcuts", list.joinToString(",")).apply()

    /** مجموعات التطبيقات */
    fun groups(c: Context): List<AppGroup> = AppGroup.listFromJson(p(c).getString("groups", "[]"))

    fun setGroups(c: Context, list: List<AppGroup>) =
        p(c).edit().putString("groups", AppGroup.listToJson(list)).apply()
}
