package com.tvremote.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

/**
 * حفظ تخصيصات الأزرار في SharedPreferences (تبقى بعد إغلاق التطبيق وإعادة تشغيل الجهاز).
 * المفتاح: "k_<keyCode>"  ←  القيمة: اسم الوظيفة (RemoteAction.name)
 *
 * ملاحظة: الواجهة والخدمة تعملان في نفس العملية (process) وتتشاركان نفس كائن
 * SharedPreferences، لذلك أي تعديل يصل للخدمة فوراً بدون إعادة تشغيل.
 */
object KeyMappingStore {

    private const val PREFS = "key_mappings"
    private const val PREFIX = "k_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, keyCode: Int): RemoteAction? =
        RemoteAction.fromName(prefs(context).getString(PREFIX + keyCode, null))

    fun put(context: Context, keyCode: Int, action: RemoteAction) {
        prefs(context).edit().putString(PREFIX + keyCode, action.name).apply()
    }

    fun remove(context: Context, keyCode: Int) {
        prefs(context).edit().remove(PREFIX + keyCode).apply()
    }

    /** كل التخصيصات مرتبة حسب اسم الزر */
    fun getAll(context: Context): List<Pair<Int, RemoteAction>> =
        prefs(context).all.mapNotNull { (key, value) ->
            val code = key.removePrefix(PREFIX).toIntOrNull()
            val action = RemoteAction.fromName(value as? String)
            if (code != null && action != null) code to action else null
        }.sortedBy { keyLabel(it.first) }

    /** اسم مقروء للزر بدلاً من رقم keyCode */
    fun keyLabel(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_MOVE_HOME -> "Home"
        KeyEvent.KEYCODE_MOVE_END -> "End"
        KeyEvent.KEYCODE_PAGE_UP -> "Page Up"
        KeyEvent.KEYCODE_PAGE_DOWN -> "Page Down"
        KeyEvent.KEYCODE_INSERT -> "Insert"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_SYSRQ -> "Print Screen"
        KeyEvent.KEYCODE_SCROLL_LOCK -> "Scroll Lock"
        KeyEvent.KEYCODE_BREAK -> "Pause/Break"
        else -> KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .replace('_', ' ')
    }
}
