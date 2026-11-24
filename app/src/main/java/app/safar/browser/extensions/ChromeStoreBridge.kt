package app.safar.browser.extensions

import app.safar.browser.translate.providers.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** بديل موقّع على متجر Firefox (AMO). */
data class AmoMatch(val name: String, val slug: String, val users: Int, val xpiUrl: String)

data class StoreAnalysis(
    val id: String,
    val compat: ChromeStore.Compat,
    val displayName: String,
    val amo: List<AmoMatch>,
)

/** تنزيل حزمة الإضافة من متجر Chrome وتحليلها والبحث عن نسختها الموقّعة على AMO. */
class ChromeStoreBridge {

    suspend fun analyze(storeUrl: String, pageTitle: String?): StoreAnalysis = withContext(Dispatchers.IO) {
        val id = ChromeStore.extensionId(storeUrl) ?: throw IllegalArgumentException("افتح صفحة إضافة محددة في المتجر أولاً")
        val crx = Http.client.newCall(
            Request.Builder().url(ChromeStore.crxDownloadUrl(id)).header("User-Agent", DESKTOP_CHROME_UA).build()
        ).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("تعذر تنزيل الحزمة من Google (HTTP ${res.code})")
            res.body!!.bytes()
        }
        val compat = ChromeStore.analyze(ChromeStore.readManifest(crx))
        val name = ChromeStore.displayName(compat.name, pageTitle)
        StoreAnalysis(id, compat, name, searchAmo(name))
    }

    /** البحث في متجر Firefox عن إضافات متوافقة مع أندرويد بنفس الاسم. */
    suspend fun searchAmo(name: String): List<AmoMatch> = withContext(Dispatchers.IO) {
        val url = "https://addons.mozilla.org/api/v5/addons/search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", name)
            .addQueryParameter("app", "android")
            .addQueryParameter("type", "extension")
            .addQueryParameter("sort", "relevance")
            .addQueryParameter("page_size", "5")
            .build()
        runCatching {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                parseAmo(res.body!!.string())
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun parseAmo(body: String): List<AmoMatch> {
            val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
            return results.mapNotNull { r ->
                val o = r as? JsonObject ?: return@mapNotNull null
                val slug = (o["slug"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val nameEl = o["name"]
                val name = when (nameEl) {
                    is JsonPrimitive -> nameEl.contentOrNull
                    is JsonObject -> (nameEl["ar"] ?: nameEl["en-US"] ?: nameEl.values.firstOrNull())?.let { (it as? JsonPrimitive)?.contentOrNull }
                    else -> null
                } ?: slug
                val users = (o["average_daily_users"] as? JsonPrimitive)?.intOrNull ?: 0
                AmoMatch(name, slug, users, "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi")
            }
        }
    }
}

