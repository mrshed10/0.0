package com.deepsearch.hub.search

import java.util.Base64

/**
 * محرك بحث عكسي.
 * نرفع الصورة بمحاكاة نموذج HTML حقيقي (multipart/form-data) داخل الـ WebView،
 * فلا نحتاج أي مفاتيح API ونحصل على صفحة النتائج الكاملة للمحرك.
 *
 * @param action     عنوان استقبال الرفع.
 * @param field      اسم حقل الملف في النموذج.
 * @param base64Field true إذا كان المحرك يتوقع الصورة كنص Base64 بدلاً من ملف.
 * @param homeUrl    صفحة الرفع اليدوي (احتياطي: الصورة تُرفق تلقائياً عند الضغط على زر الرفع).
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val action: String,
    val field: String,
    val base64Field: Boolean = false,
    val extraFields: Map<String, String> = emptyMap(),
    val homeUrl: String,
)

object SearchEngines {

    fun all(now: Long = System.currentTimeMillis()): List<SearchEngine> = listOf(
        SearchEngine(
            id = "google", name = "Google Lens",
            action = "https://lens.google.com/v3/upload?hl=en&re=df&ep=gisbubb&st=$now",
            field = "encoded_image",
            homeUrl = "https://lens.google.com/",
        ),
        SearchEngine(
            id = "yandex", name = "Yandex",
            action = "https://yandex.com/images/search?rpt=imageview",
            field = "upfile",
            homeUrl = "https://yandex.com/images/",
        ),
        SearchEngine(
            id = "bing", name = "Bing Visual",
            action = "https://www.bing.com/images/search?view=detailv2&iss=sbiupload&FORM=SBIIDP",
            field = "imageBin", base64Field = true,
            extraFields = mapOf("cbir" to "sbi"),
            homeUrl = "https://www.bing.com/visualsearch",
        ),
        SearchEngine(
            id = "tineye", name = "TinEye",
            action = "https://tineye.com/search",
            field = "image",
            homeUrl = "https://tineye.com/",
        ),
        SearchEngine(
            id = "saucenao", name = "SauceNAO",
            action = "https://saucenao.com/search.php",
            field = "file",
            homeUrl = "https://saucenao.com/",
        ),
    )

    fun byIds(ids: Collection<String>): List<SearchEngine> =
        all().filter { it.id in ids }.ifEmpty { all() }

    /**
     * صفحة محلية صغيرة تنشئ ملفاً من بيانات JPEG وترفعه للمحرك تلقائياً.
     * DataTransfer يسمح بتعبئة input[type=file] برمجياً في Chromium/WebView.
     */
    fun buildSubmitPage(engine: SearchEngine, jpeg: ByteArray, uploadingLabel: String): String {
        val b64 = Base64.getEncoder().encodeToString(jpeg)
        val hidden = engine.extraFields.entries.joinToString("") { (k, v) ->
            "<input type=\"hidden\" name=\"${attr(k)}\" value=\"${attr(v)}\">"
        }
        val imageInput = if (engine.base64Field) {
            "<input type=\"hidden\" name=\"${attr(engine.field)}\" value=\"$b64\">"
        } else {
            "<input type=\"file\" id=\"file\" name=\"${attr(engine.field)}\" style=\"display:none\">"
        }
        val fillScript = if (engine.base64Field) "" else """
            var bin = atob(B64), bytes = new Uint8Array(bin.length);
            for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
            var dt = new DataTransfer();
            dt.items.add(new File([bytes], 'image.jpg', { type: 'image/jpeg' }));
            document.getElementById('file').files = dt.files;
        """.trimIndent()
        val b64Var = if (engine.base64Field) "\"\"" else "\"$b64\""
        return """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:90vh;color:#888}</style>
            </head><body><div>${attr(uploadingLabel)} ${attr(engine.name)}…</div>
            <form id="f" method="post" enctype="multipart/form-data" action="${attr(engine.action)}">$hidden$imageInput</form>
            <script>
            var B64 = $b64Var;
            try { $fillScript } catch (e) { document.body.innerText = 'Upload error: ' + e; }
            document.getElementById('f').submit();
            </script></body></html>
        """.trimIndent()
    }

    private fun attr(s: String) = s
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
