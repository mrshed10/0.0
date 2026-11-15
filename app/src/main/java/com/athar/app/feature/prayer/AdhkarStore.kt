package com.athar.app.feature.prayer

import org.json.JSONArray
import org.json.JSONObject

/** A user-editable dhikr. */
data class UserDhikr(val id: Long, val text: String, val count: Int)

data class AdhkarLists(val morning: List<UserDhikr>, val evening: List<UserDhikr>) {
    fun list(evening: Boolean) = if (evening) this.evening else morning

    fun replace(evening: Boolean, items: List<UserDhikr>) =
        if (evening) copy(evening = items) else copy(morning = items)

    val nextId: Long get() = ((morning + evening).maxOfOrNull { it.id } ?: 0) + 1

    companion object {
        /** The built-in morning and evening adhkar. */
        fun defaults(): AdhkarLists {
            var id = 0L
            fun map(list: List<Dhikr>) = list.map { UserDhikr(++id, it.ar, it.count) }
            return AdhkarLists(map(SpiritualContent.morning), map(SpiritualContent.evening))
        }
    }
}

object AdhkarJson {
    fun encode(lists: AdhkarLists): String = JSONObject()
        .put("morning", array(lists.morning))
        .put("evening", array(lists.evening))
        .toString()

    fun decode(raw: String?): AdhkarLists? = runCatching {
        val o = JSONObject(raw ?: return null)
        AdhkarLists(list(o.getJSONArray("morning")), list(o.getJSONArray("evening")))
    }.getOrNull()

    private fun array(items: List<UserDhikr>) = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("count", it.count)) }
    }

    private fun list(a: JSONArray) = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        UserDhikr(o.getLong("id"), o.getString("text"), o.optInt("count", 1).coerceIn(1, 10_000))
    }
}
