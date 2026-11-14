package com.athar.app.core.notify

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.athar.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Notification sounds: system tones or audio files the user imported. */
object Tones {
    fun authority(context: Context) = "${context.packageName}.tones"

    fun isImported(context: Context, uri: Uri) = uri.authority == authority(context)

    /** Human-readable name of a tone; "Default" for null. */
    fun title(context: Context, tone: String?): String {
        if (tone.isNullOrEmpty()) return context.getString(R.string.tone_default)
        val uri = Uri.parse(tone)
        if (isImported(context, uri)) return uri.lastPathSegment?.substringBeforeLast('.') ?: "?"
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            ?: context.getString(R.string.tone_custom)
    }

    /** Copies a picked audio file into app storage; returns a shareable content URI string. */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "tone.mp3"
            val safe = name.replace(Regex("""[^\p{L}\p{N}._ -]"""), "_").take(80)
            val dir = File(context.filesDir, "tones").apply { mkdirs() }
            val file = File(dir, safe)
            context.contentResolver.openInputStream(source)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            FileProvider.getUriForFile(context, authority(context), file).toString()
        }.getOrNull()
    }
}
