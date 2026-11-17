package com.athar.app.core.notify

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Speaks prayer announcements with the phone's text-to-speech engine (Arabic
 * when the app is in Arabic and the engine has it). Never throws: if speech is
 * unavailable it simply stays silent.
 */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()

    init {
        tts = runCatching {
            TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        }.getOrElse {
            ready.complete(false)
            null
        }
    }

    /** Suspends until [text] has been spoken (max ~20 s). */
    suspend fun speak(text: String, alarmStream: Boolean, volume: Float) {
        val engine = tts ?: return
        val ok = withTimeoutOrNull(5_000) { ready.await() } ?: false
        if (!ok) return
        val appLocale = context.resources.configuration.locales[0]
        val wanted = if (appLocale.language == "ar") Locale("ar") else appLocale
        val result = engine.setLanguage(wanted)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(0.9f)
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
        withTimeoutOrNull(20_000) { done.await() }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}
