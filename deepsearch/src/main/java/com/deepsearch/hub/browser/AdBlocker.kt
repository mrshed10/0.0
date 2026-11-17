package com.deepsearch.hub.browser

import android.content.Context
import com.deepsearch.hub.dns.DohClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * حاجب إعلانات خفيف قائم على قوائم hosts.
 * - قائمة مدمجة صغيرة في assets/adblock_hosts.txt
 * - قائمة كاملة اختيارية يتم تنزيلها (StevenBlack ~ 100 ألف نطاق) وتُحفظ محلياً.
 */
object AdBlocker {

    const val DEFAULT_LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val REMOTE_FILE = "hosts_remote.txt"

    @Volatile
    private var hosts: Set<String> = emptySet()

    val size: Int get() = hosts.size

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val set = HashSet<String>(4096)
        runCatching {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { parseInto(it, set) }
        }
        val remote = File(context.filesDir, REMOTE_FILE)
        if (remote.exists()) runCatching { remote.bufferedReader().useLines { parseInto(it, set) } }
        hosts = set
    }

    /** ينزّل قائمة hosts (عبر DoH) ويعيد عدد النطاقات المحجوبة. */
    suspend fun updateFromUrl(context: Context, url: String = DEFAULT_LIST_URL): Int = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        DohClient.httpClient().newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val tmp = File(context.filesDir, "$REMOTE_FILE.tmp")
            r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(File(context.filesDir, REMOTE_FILE))
        }
        init(context)
        size
    }

    fun isBlocked(host: String?): Boolean = matches(hosts, host)

    // ---- منطق نقي قابل للاختبار ----

    private val IGNORED = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0", "ip6-localhost")

    fun parseInto(lines: Sequence<String>, into: MutableSet<String>) {
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val host = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> continue
            }.lowercase()
            if (host !in IGNORED && host.contains('.')) into.add(host)
        }
    }

    /** يطابق النطاق أو أي نطاق أب له: ads.example.com يطابق example.com إذا كان محجوباً. */
    fun matches(set: Set<String>, host: String?): Boolean {
        if (host.isNullOrEmpty() || set.isEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false // لا نحجب نطاقات عليا مثل "com"
        }
    }
}
