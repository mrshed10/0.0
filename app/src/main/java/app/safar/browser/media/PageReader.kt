package app.safar.browser.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** "الاستماع إلى هذه الصفحة": قراءة نص الصفحة بمحرك النطق في النظام، مقسماً إلى جمل. */
class PageReader(context: Context) {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    private var ready = false
    private var pending: Pair<String, String>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        pending?.let { (t, l) -> pending = null; speak(t, l) }
    }.also {
        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == LAST) _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { _speaking.value = false }
        })
    }

    fun speak(text: String, lang: String) {
        if (!ready) { pending = text to lang; return }
        val locale = lang.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        tts.stop()
        val chunks = chunk(text)
        chunks.forEachIndexed { i, c ->
            tts.speak(c, TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) LAST else "p$i")
        }
        _speaking.value = chunks.isNotEmpty()
    }

    fun stop() { tts.stop(); _speaking.value = false }

    fun shutdown() = tts.shutdown()

    companion object {
        private const val LAST = "last"

        /** تقسيم النص إلى مقاطع ≤ 3500 حرف عند نهايات الجمل (حد محرك النطق ~4000). */
        fun chunk(text: String, max: Int = 3500): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            for (sentence in text.split(Regex("""(?<=[.!?؟。\n])\s+"""))) {
                if (sentence.isBlank()) continue
                if (sb.length + sentence.length + 1 > max && sb.isNotEmpty()) { out += sb.toString(); sb.setLength(0) }
                if (sentence.length > max) sentence.chunked(max).forEach { out += it } else sb.append(sentence).append(' ')
            }
            if (sb.isNotBlank()) out += sb.toString().trim()
            return out
        }
    }
}
