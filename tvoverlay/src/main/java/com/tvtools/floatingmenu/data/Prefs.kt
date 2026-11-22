package com.tvtools.floatingmenu.data

import android.content.Context

/** ترميز قائمة التطبيقات المفضلة كنص واحد مع الحفاظ على الترتيب (StringSet لا يحفظ الترتيب). */
object FavoritesCodec {
    private const val SEP = "|"

    fun encode(packages: List<String>): String =
        packages.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(SEP)

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(SEP).map(String::trim).filter(String::isNotEmpty).distinct()
}

/** كل الإعدادات محفوظة محلياً في SharedPreferences وتبقى بعد إعادة التشغيل. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("floating_menu", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = FavoritesCodec.decode(sp.getString(KEY_FAVORITES, null))
        set(value) = sp.edit().putString(KEY_FAVORITES, FavoritesCodec.encode(value)).apply()

    fun isFavorite(packageName: String) = packageName in favorites

    fun removeFavorite(packageName: String) {
        favorites = favorites - packageName
    }

    /** يضيف التطبيق أو يزيله، ويعيد true إذا أصبح مضافاً. */
    fun toggleFavorite(packageName: String): Boolean {
        val current = favorites
        return if (packageName in current) {
            favorites = current - packageName
            false
        } else {
            favorites = current + packageName
            true
        }
    }

    /** موضع الزر العائم بالبكسل؛ ‎-1 يعني الموضع الافتراضي. */
    var bubbleX: Int
        get() = sp.getInt(KEY_BUBBLE_X, -1)
        set(value) = sp.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = sp.getInt(KEY_BUBBLE_Y, -1)
        set(value) = sp.edit().putInt(KEY_BUBBLE_Y, value).apply()

    fun resetBubblePosition() {
        sp.edit().remove(KEY_BUBBLE_X).remove(KEY_BUBBLE_Y).apply()
    }

    var bubbleVisible: Boolean
        get() = sp.getBoolean(KEY_BUBBLE_VISIBLE, true)
        set(value) = sp.edit().putBoolean(KEY_BUBBLE_VISIBLE, value).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = sp.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    /** هل يريد المستخدم أن تعمل الخدمة؟ (يُستخدم عند الإقلاع وعند فتح التطبيق). */
    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var firstRun: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN, true)
        set(value) = sp.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_BUBBLE_VISIBLE = "bubble_visible"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_FIRST_RUN = "first_run"
    }
}
