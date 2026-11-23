package app.safar.browser.translate.providers

import app.safar.browser.translate.ProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun postJson(url: String, body: JsonElement, headers: Map<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).post(body.toString().toRequestBody(jsonType))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ProviderException("HTTP ${res.code}: ${text.take(300)}")
                Json.parseToJsonElement(text)
            }
        }
}
