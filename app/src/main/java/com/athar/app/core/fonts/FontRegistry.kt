package com.athar.app.core.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.athar.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class FontScript { Arabic, Latin }

/** A selectable font: bundled in the app or imported by the user. */
data class FontInfo(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val script: FontScript,
    @FontRes val regular: Int? = null,
    @FontRes val medium: Int? = null,
    @FontRes val bold: Int? = null,
    val path: String? = null,
) {
    val imported: Boolean get() = path != null
    fun name(locale: Locale) = if (locale.language == "ar") nameAr else nameEn
}

/**
 * All fonts the app knows about. Compose families are used for the app UI;
 * Android [Typeface]s for widget text (drawn as images, since widgets cannot
 * load custom fonts).
 */
object FontRegistry {
    const val DEFAULT_UI = "tajawal"
    const val DEFAULT_DECORATIVE = "amiri"

    val bundled = listOf(
        FontInfo("tajawal", "تجوّل", "Tajawal", FontScript.Arabic, R.font.tajawal_regular, R.font.tajawal_medium, R.font.tajawal_bold),
        FontInfo("amiri", "أميري (نسخ)", "Amiri (Naskh)", FontScript.Arabic, R.font.amiri_regular, null, R.font.amiri_bold),
        FontInfo("scheherazade", "شهرزاد", "Scheherazade", FontScript.Arabic, R.font.scheherazadenew_regular, null, R.font.scheherazadenew_bold),
        FontInfo("arefruqaa", "عارف رقعة", "Aref Ruqaa", FontScript.Arabic, R.font.arefruqaa_regular, null, R.font.arefruqaa_bold),
        FontInfo("lalezar", "لاله‌زار", "Lalezar", FontScript.Arabic, R.font.lalezar_regular),
        FontInfo("rakkas", "ركاس", "Rakkas", FontScript.Arabic, R.font.rakkas_regular),
        FontInfo("poppins", "Poppins", "Poppins", FontScript.Latin, R.font.poppins_regular, R.font.poppins_medium, R.font.poppins_bold),
        FontInfo("cinzel", "Cinzel", "Cinzel", FontScript.Latin, R.font.cinzel_regular),
        FontInfo("bebasneue", "Bebas Neue", "Bebas Neue", FontScript.Latin, R.font.bebasneue_regular),
        FontInfo("abrilfatface", "Abril Fatface", "Abril Fatface", FontScript.Latin, R.font.abrilfatface_regular),
        FontInfo("anton", "Anton", "Anton", FontScript.Latin, R.font.anton_regular),
    )

    private const val PREFS = "athar_fonts"
    private const val KEY_IMPORTED = "imported"

    fun imported(context: Context): List<FontInfo> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_IMPORTED, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val name = o.getString("name")
            FontInfo(
                id = o.getString("id"),
                nameAr = name,
                nameEn = name,
                script = if (o.optString("script") == FontScript.Latin.name) FontScript.Latin else FontScript.Arabic,
                path = o.getString("path"),
            )
        }.filter { File(it.path!!).exists() }
    }.getOrDefault(emptyList())

    fun all(context: Context): List<FontInfo> = bundled + imported(context)

    fun find(context: Context, id: String?): FontInfo? = id?.let { wanted -> all(context).firstOrNull { it.id == wanted } }

    /**
     * Copies a TTF/OTF into app storage after checking Android can load it.
     * Returns the new font, or null if the file is not a usable font.
     */
    fun import(context: Context, uri: Uri, script: FontScript): FontInfo? = runCatching {
        val display = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
        val ext = display.substringAfterLast('.', "ttf").lowercase().takeIf { it == "ttf" || it == "otf" } ?: "ttf"
        val id = "user_" + System.currentTimeMillis()
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val file = File(dir, "$id.$ext")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        if (!isValidFont(file)) {
            file.delete()
            return null
        }
        val info = FontInfo(id, display.substringBeforeLast('.'), display.substringBeforeLast('.'), script, path = file.absolutePath)
        val array = JSONArray(prefs(context).getString(KEY_IMPORTED, "[]"))
        array.put(JSONObject().put("id", id).put("name", info.nameEn).put("path", info.path).put("script", script.name))
        prefs(context).edit().putString(KEY_IMPORTED, array.toString()).apply()
        info
    }.getOrNull()

    fun delete(context: Context, id: String) {
        val all = imported(context)
        all.firstOrNull { it.id == id }?.path?.let { File(it).delete() }
        val array = JSONArray()
        all.filter { it.id != id }.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.nameEn).put("path", it.path).put("script", it.script.name))
        }
        prefs(context).edit().putString(KEY_IMPORTED, array.toString()).apply()
    }

    private fun isValidFont(file: File): Boolean {
        if (file.length() < 1_000) return false
        return if (Build.VERSION.SDK_INT >= 29) {
            runCatching { android.graphics.fonts.Font.Builder(file).build() }.isSuccess
        } else {
            runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { it != Typeface.DEFAULT } ?: false
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- Compose

    /**
     * Family for app text. With a separate [latinId] (Android 10+), Latin
     * letters use that font and everything else falls back to [primaryId].
     */
    fun composeFamily(context: Context, primaryId: String?, latinId: String? = null): FontFamily {
        val primary = find(context, primaryId) ?: bundled.first()
        val latin = find(context, latinId)
        if (latin == null || latin.id == primary.id || Build.VERSION.SDK_INT < 29) return single(primary)
        return runCatching {
            val tf = Typeface.CustomFallbackBuilder(platformFamily(context, latin))
                .addCustomFallback(platformFamily(context, primary))
                .build()
            FontFamily(androidx.compose.ui.text.font.Typeface(tf))
        }.getOrElse { single(primary) }
    }

    private fun single(info: FontInfo): FontFamily {
        info.path?.let { path ->
            return runCatching { FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromFile(path))) }
                .getOrElse { FontFamily.Default }
        }
        val fonts = buildList {
            info.regular?.let { add(Font(it, FontWeight.Normal)) }
            info.medium?.let { add(Font(it, FontWeight.Medium)) }
            info.bold?.let { add(Font(it, FontWeight.Bold)) }
        }
        return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }

    @androidx.annotation.RequiresApi(29)
    private fun platformFamily(context: Context, info: FontInfo): android.graphics.fonts.FontFamily {
        val res = context.resources
        info.path?.let { return android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(File(it)).build()).build() }
        val builder = android.graphics.fonts.FontFamily.Builder(
            android.graphics.fonts.Font.Builder(res, info.regular!!).setWeight(400).build(),
        )
        info.medium?.let { builder.addFont(android.graphics.fonts.Font.Builder(res, it).setWeight(500).build()) }
        info.bold?.let { builder.addFont(android.graphics.fonts.Font.Builder(res, it).setWeight(700).build()) }
        return builder.build()
    }

    // ---------------------------------------------------------------- Widgets

    /** Typeface for widget text; null means "use the system font" (normal widget text). */
    fun typeface(context: Context, id: String?): Typeface? {
        val info = find(context, id) ?: return null
        return runCatching {
            info.path?.let { Typeface.createFromFile(it) } ?: ResourcesCompat.getFont(context, info.regular!!)
        }.getOrNull()
    }

    /** Bold variant if the font ships one, otherwise synthetic bold. */
    fun boldTypeface(context: Context, id: String?, regular: Typeface): Typeface =
        find(context, id)?.bold?.let { runCatching { ResourcesCompat.getFont(context, it) }.getOrNull() }
            ?: Typeface.create(regular, Typeface.BOLD)
}
