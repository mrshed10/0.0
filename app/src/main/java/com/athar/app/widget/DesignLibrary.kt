package com.athar.app.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A widget design the user saved (their own or imported). */
data class SavedDesign(
    val id: String,
    val name: String,
    val config: WidgetConfig,
    val wide: Boolean,
    val createdAt: Long,
)

/** "My designs": saved widget designs, kept on the device. */
class DesignLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("athar_designs", Context.MODE_PRIVATE)
    private val _designs = MutableStateFlow(DesignCodec.decodeList(prefs.getString(KEY, null)))
    val designs: StateFlow<List<SavedDesign>> = _designs.asStateFlow()

    fun save(design: SavedDesign) = update { list ->
        if (list.any { it.id == design.id }) list.map { if (it.id == design.id) design else it } else listOf(design) + list
    }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun duplicate(id: String, copySuffix: String) = update { list ->
        val original = list.firstOrNull { it.id == id } ?: return@update list
        listOf(original.copy(id = newId(), name = original.name + " " + copySuffix, createdAt = System.currentTimeMillis())) + list
    }

    fun addAll(designs: List<SavedDesign>) = update { designs + it }

    private fun update(transform: (List<SavedDesign>) -> List<SavedDesign>) {
        val value = transform(_designs.value)
        prefs.edit().putString(KEY, DesignCodec.encodeList(value)).apply()
        _designs.value = value
    }

    companion object {
        private const val KEY = "designs"
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Shareable design format (JSON). One design:
 * {"format":"athar-widget","version":1,"name":..,"wide":..,"config":{..}}
 * or a pack: {"format":"athar-widget-pack","version":1,"designs":[..]}.
 * Local photo backgrounds are not included (the file stays small and private).
 */
object DesignCodec {
    private const val FORMAT = "athar-widget"
    private const val PACK = "athar-widget-pack"
    private const val VERSION = 1

    fun export(designs: List<SavedDesign>): String {
        val clean = designs.map { it.copy(config = withoutLocalImage(it.config)) }
        val o = if (clean.size == 1) {
            designObject(clean.first()).put("format", FORMAT)
        } else {
            JSONObject().put("format", PACK).put("designs", JSONArray(clean.map { designObject(it) }))
        }
        return o.put("version", VERSION).toString(2)
    }

    /** Parses shared JSON into new designs (fresh ids). Throws [IllegalArgumentException] if invalid. */
    fun import(raw: String): List<SavedDesign> {
        val o = runCatching { JSONObject(raw.trim()) }.getOrElse { throw IllegalArgumentException("not JSON") }
        val items = when (o.optString("format")) {
            FORMAT -> listOf(o)
            PACK -> o.optJSONArray("designs")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            else -> throw IllegalArgumentException("unknown format")
        }
        val now = System.currentTimeMillis()
        val designs = items.mapIndexedNotNull { i, d -> fromObject(d, DesignLibrary.newId(), now + i) }
        require(designs.isNotEmpty()) { "no designs" }
        return designs
    }

    fun encodeList(list: List<SavedDesign>): String = JSONArray(list.map { designObject(it).put("id", it.id).put("createdAt", it.createdAt) }).toString()

    fun decodeList(raw: String?): List<SavedDesign> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            fromObject(o, o.optString("id").ifBlank { DesignLibrary.newId() }, o.optLong("createdAt", 0))
        }
    }.getOrDefault(emptyList())

    private fun designObject(d: SavedDesign) = JSONObject()
        .put("name", d.name)
        .put("wide", d.wide)
        .put("config", WidgetJson.encodeObject(d.config))

    private fun fromObject(o: JSONObject, id: String, createdAt: Long): SavedDesign? {
        val config = o.optJSONObject("config")?.let { WidgetJson.decodeObject(it) } ?: return null
        return SavedDesign(id, o.optString("name").take(60).ifBlank { "Widget" }, config, o.optBoolean("wide", true), createdAt)
    }

    private fun withoutLocalImage(c: WidgetConfig): WidgetConfig =
        if (c.style.imagePath == null) c
        else c.copy(style = c.style.copy(imagePath = null, kind = if (c.style.kind == BackgroundKind.Image) BackgroundKind.Gradient else c.style.kind))
}
