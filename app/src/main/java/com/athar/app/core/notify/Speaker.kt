package com.athar.app.core.notify

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Speaks prayer announcements with the phone's text-to-speech engine. Uses the
 * engine and voice the user picked; otherwise the most natural (highest
 * quality, on-device) voice for the app's language. Never throws: if speech is
 * unavailable it stays silent.
 */
class Speaker(private val context: Context, engine: String? = null) {
    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()

    init {
        val listener = TextToSpeech.OnInitListener { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        tts = runCatching {
            if (engine.isNullOrBlank()) TextToSpeech(context.applicationContext, listener) else TextToSpeech(context.applicationContext, listener, engine)
        }.getOrElse {
            ready.complete(false)
            null
        }
    }

    /** True once the engine is ready. */
    suspend fun awaitReady(): Boolean = tts != null && (withTimeoutOrNull(5_000) { ready.await() } ?: false)

    /** Installed text-to-speech engines (package to label). */
    fun engines(): List<Pair<String, String>> = runCatching { tts?.engines.orEmpty().map { it.name to it.label } }.getOrDefault(emptyList())

    /** Voices for the app's language, most natural first. */
    fun voices(): List<Voice> {
        val engine = tts ?: return emptyList()
        val language = wantedLocale().language
        return runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
    }

    private fun wantedLocale(): Locale {
        val appLocale = context.resources.configuration.locales[0]
        return if (appLocale.language == "ar") Locale("ar") else appLocale
    }

    /** Suspends until [text] has been spoken (max ~25 s). */
    suspend fun speak(
        text: String,
        alarmStream: Boolean,
        volume: Float,
        voiceName: String? = null,
        rate: Float = 0.85f,
        pitch: Float = 1f,
    ) {
        val engine = tts ?: return
        if (!awaitReady()) return
        val chosen = voiceName?.let { name -> runCatching { engine.voices.orEmpty().firstOrNull { it.name == name } }.getOrNull() }
        val voice = chosen ?: voices().firstOrNull { !it.isNetworkConnectionRequired } ?: voices().firstOrNull()
        val applied = voice != null && runCatching { engine.setVoice(voice) == TextToSpeech.SUCCESS }.getOrDefault(false)
        if (!applied) {
            val result = engine.setLanguage(wantedLocale())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
        engine.setPitch(pitch.coerceIn(0.5f, 1.5f))
        val done = CompletableDeferred<Unit>()
        val id = "athar_${System.nanoTime()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
            override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done.complete(Unit) }
        })
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.1f, 1f)) }
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) return
        withTimeoutOrNull(25_000) { done.await() }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    companion object {
        /** A readable label for a voice, e.g. "Voice 2 · natural · on device". */
        fun describe(index: Int, voice: Voice, natural: String, network: String, local: String): String {
            val quality = if (voice.quality >= Voice.QUALITY_HIGH) " · $natural" else ""
            val where = if (voice.isNetworkConnectionRequired) network else local
            return "${index + 1}$quality · $where"
        }
    }
}
