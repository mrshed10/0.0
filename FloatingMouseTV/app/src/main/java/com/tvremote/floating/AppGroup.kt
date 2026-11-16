package com.tvremote.floating

import org.json.JSONArray
import org.json.JSONObject

/** طريقة فتح تطبيقات المجموعة */
enum class LaunchMode(val labelRes: Int) {
    /** فتح التطبيقات واحداً تلو الآخر (الأخير يبقى أمامك) */
    SEQUENTIAL(R.string.mode_sequential),

    /** تقسيم الشاشة بين أول تطبيقين */
    SPLIT(R.string.mode_split),

    /** نوافذ عائمة متجاورة (Freeform) */
    WINDOWS(R.string.mode_windows);

    companion object {
        fun fromName(name: String?) = values().firstOrNull { it.name == name } ?: SEQUENTIAL
    }
}

/** مجموعة تطبيقات تُفتح معاً بنقرة واحدة */
data class AppGroup(
    val id: String,
    val name: String,
    val packages: List<String>,
    val mode: LaunchMode
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("mode", mode.name)
        .put("packages", JSONArray(packages))

    companion object {
        fun fromJson(o: JSONObject): AppGroup {
            val arr = o.optJSONArray("packages") ?: JSONArray()
            return AppGroup(
                id = o.optString("id"),
                name = o.optString("name"),
                packages = (0 until arr.length()).map { arr.getString(it) },
                mode = LaunchMode.fromName(o.optString("mode"))
            )
        }

        fun listToJson(list: List<AppGroup>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<AppGroup> = try {
            val arr = JSONArray(json ?: "[]")
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
