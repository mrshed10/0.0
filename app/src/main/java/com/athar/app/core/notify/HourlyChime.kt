package com.athar.app.core.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.athar.app.core.data.ChimeSettings
import com.athar.app.core.data.ChimeSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The hourly "beep-beep" of old digital watches. The tone is generated (no
 * sound file) and played once, quietly, through the system sounds stream.
 */
object HourlyChime {
    const val SAMPLE_RATE = 44_100

    /** The next time the chime should sound: the top of a coming hour outside quiet hours. */
    fun nextAt(now: LocalDateTime, settings: ChimeSettings): LocalDateTime? {
        if (!settings.enabled) return null
        var at = now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        repeat(24) {
            if (!settings.isQuiet(at.hour)) return at
            at = at.plusHours(1)
        }
        return null // every hour is quiet
    }

    /** True if the phone should not make the sound now (call in progress, silent/vibrate when respected). */
    fun shouldStayQuiet(context: Context, settings: ChimeSettings): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return true
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return true
        return settings.respectSilent && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    /** Plays the chime (blocks until it ends; call off the main thread). [test] ignores silent mode. */
    suspend fun play(context: Context, settings: ChimeSettings, test: Boolean = false) = withContext(Dispatchers.Default) {
        if (!test && shouldStayQuiet(context, settings)) return@withContext
        val samples = samples(settings.sound)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        }.getOrNull() ?: return@withContext
        try {
            track.write(samples, 0, samples.size)
            track.setVolume(settings.volume / 100f)
            track.play()
            delay(samples.size * 1000L / SAMPLE_RATE + 120)
            track.stop()
        } catch (e: Exception) {
            // Audio device busy or unavailable: skip this hour quietly.
        } finally {
            track.release()
        }
    }

    /** PCM samples for a chime sound. */
    fun samples(sound: ChimeSound): ShortArray = when (sound) {
        // Two short, bright beeps: the classic digital-watch hour signal.
        ChimeSound.Classic -> join(beep(3_200.0, 0.075, digital = true), silence(0.085), beep(3_200.0, 0.075, digital = true))
        ChimeSound.Single -> beep(3_200.0, 0.11, digital = true)
        // Softer two-note chime with a gentle fade.
        ChimeSound.Soft -> join(bell(1_760.0, 0.16), bell(1_320.0, 0.28))
    }

    private fun beep(freq: Double, seconds: Double, digital: Boolean): ShortArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val ramp = (SAMPLE_RATE * 0.004).toInt() // 4 ms fade in/out: no clicks
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var v = sin(2 * PI * freq * t)
            if (digital) v = (v + 0.22 * sin(2 * PI * freq * 3 * t)) / 1.22 // a touch of "piezo" edge
            val env = min(1.0, min(i, n - 1 - i).toDouble() / ramp)
            (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun bell(freq: Double, seconds: Double): ShortArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val attack = (SAMPLE_RATE * 0.006).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val v = sin(2 * PI * freq * t) * 0.8 + sin(2 * PI * freq * 2 * t) * 0.2
            val env = min(1.0, i.toDouble() / attack) * exp(-t * 9)
            (v * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun silence(seconds: Double) = ShortArray((SAMPLE_RATE * seconds).toInt())

    private fun join(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        parts.forEach { it.copyInto(out, at); at += it.size }
        return out
    }
}
