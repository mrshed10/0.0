package app.safar.browser.translate

import app.safar.browser.data.ContentKind
import app.safar.browser.data.LANGUAGES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** تعليمات موحّدة لنماذج الذكاء الاصطناعي (Claude / Gemini) + تحليل الرد بأمان. */
object LlmPrompt {

    fun languageName(code: String) =
        if (code == "auto") "auto-detect" else LANGUAGES.firstOrNull { it.code == code }?.let { "${it.code} (${it.nameAr})" } ?: code

    fun system(req: TranslationRequest): String = buildString {
        appendLine("You are a professional literary and localization translator embedded in a web browser.")
        appendLine("Translate every input segment from ${languageName(req.source)} into ${languageName(req.target)}.")
        appendLine("Rules:")
        appendLine("- Preserve meaning, tone, register and emotion. Render slang and colloquial speech with natural equivalents in the target language, not literal word-for-word translations.")
        appendLine("- Keep proper names consistent across segments; keep honorifics (-san, -kun, -sama, ...) only if they carry meaning.")
        appendLine("- Do not add explanations, notes, romanization or quotes that are not in the source.")
        appendLine("- If a segment is already in the target language, or is a URL/code/number, return it unchanged.")
        when (req.kind) {
            ContentKind.MANGA -> {
                appendLine("- The segments are OCR output from manga/comic speech bubbles, in reading order. OCR may contain broken characters or line breaks; silently repair obvious OCR errors before translating.")
                appendLine("- Sound effects (SFX) should become short, natural onomatopoeia in the target language.")
                appendLine("- Keep translations concise so they fit inside speech bubbles.")
                appendLine("- Write it the way a professional scanlation/localization team would: natural spoken dialogue that matches each character's voice, emotion and the scene's context, never stiff word-for-word phrasing.")
                appendLine("- Segments are consecutive bubbles of the same page; use the surrounding bubbles to resolve pronouns, omitted subjects and jokes.")
            }
            ContentKind.VIDEO -> {
                appendLine("- The segments are video subtitles. Keep them short and readable at subtitle speed.")
            }
            ContentKind.TEXT -> {
                appendLine("- The segments are text nodes from a web page; some are fragments of the same sentence split by inline markup. Translate each so that concatenation still reads naturally.")
            }
        }
        if (req.target == "ar") {
            appendLine("- Arabic style: fluent, simple Modern Standard Arabic with a light conversational tone (as in professionally dubbed anime); short sentences; natural Arabic exclamations; no diacritics except where needed to avoid ambiguity.")
        }
        if (req.instructions.isNotBlank()) {
            appendLine("Additional user instructions (glossary/style):")
            appendLine(req.instructions.trim())
        }
        appendLine()
        append("Output format: reply with ONLY a JSON array of strings, exactly ${req.texts.size} items, the i-th item being the translation of the i-th segment. No markdown, no code fences.")
    }

    fun user(req: TranslationRequest): String {
        val obj = buildJsonObject {
            req.context?.let { put("context", it) }
            putJsonArray("segments") { req.texts.forEach { add(JsonPrimitive(it)) } }
        }
        return obj.toString()
    }

    /**
     * يستخرج مصفوفة JSON من الرد حتى لو أحاطها النموذج بنص أو ```json.
     * يضمن طولاً مساوياً للمدخلات: يكمل الناقص بالنص الأصلي ويقص الزائد.
     */
    fun parse(raw: String, originals: List<String>): List<String> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) throw ProviderException("LLM reply has no JSON array")
        val arr = Json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonArray
            ?: throw ProviderException("LLM reply is not an array")
        val items = arr.map { el -> runCatching { el.jsonPrimitive.contentOrNull }.getOrNull() }
        return originals.mapIndexed { i, orig -> items.getOrNull(i)?.takeIf { it.isNotBlank() } ?: orig }
    }
}
