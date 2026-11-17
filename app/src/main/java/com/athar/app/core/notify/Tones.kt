package com.athar.app.core.notify

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.athar.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One audio file the user added to the app. */
data class SavedSound(val file: File, val uri: String, val name: String, val sizeBytes: Long, val durationMs: Long?)

/**
 * Notification sounds: system tones or audio files the user imported.
 *
 * Imported files are copied into the app's private storage (files/tones), which
 * Android never clears on its own (unlike the cache), and the app only deletes
 * one when the user does so in "My sounds". A new file never replaces an
 * existing one: same-named files get a number.
 */
object Tones {
    private const val DIR = "tones"

    fun authority(context: Context) = "${context.packageName}.tones"

    fun isImported(context: Context, uri: Uri) = uri.authority == authority(context)

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /** Human-readable name of a tone; [default] (or "Default") for null. */
    fun title(context: Context, tone: String?, default: String? = null): String {
        if (tone.isNullOrEmpty()) return default ?: context.getString(R.string.tone_default)
        val uri = Uri.parse(tone)
        if (isImported(context, uri)) {
            val name = uri.lastPathSegment?.substringBeforeLast('.') ?: "?"
            val exists = fileFor(context, uri)?.exists() == true
            return if (exists) name else context.getString(R.string.sound_missing, name)
        }
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            ?: context.getString(R.string.tone_custom)
    }

    private fun fileFor(context: Context, uri: Uri): File? = uri.lastPathSegment?.let { File(dir(context), it) }

    private fun uriFor(context: Context, file: File): String =
        FileProvider.getUriForFile(context, authority(context), file).toString()

    /**
     * Copies a picked audio file into app storage; returns its content URI.
     * If the very same file was added before, that copy is reused.
     */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val (name, size) = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) (c.getString(0) to (if (c.isNull(1)) -1L else c.getLong(1))) else null }
                ?: ("sound.mp3" to -1L)
            val safe = name.replace(Regex("""[^\p{L}\p{N}._ -]"""), "_").take(80).ifBlank { "sound.mp3" }
            val folder = dir(context)
            // Same name and size: it is the same file, reuse it.
            File(folder, safe).takeIf { it.exists() && size > 0 && it.length() == size }?.let { return@runCatching uriFor(context, it) }
            val target = uniqueFile(folder, safe)
            val temp = File(folder, ".incoming_${System.nanoTime()}")
            context.contentResolver.openInputStream(source)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (temp.length() == 0L || !temp.renameTo(target)) {
                temp.delete()
                return@runCatching null
            }
            val uri = uriFor(context, target)
            grantToSystem(context, Uri.parse(uri))
            uri
        }.getOrNull()
    }

    /** "adhan.mp3" → "adhan (2).mp3" when taken. */
    internal fun uniqueFile(folder: File, name: String): File {
        var file = File(folder, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var n = 2
        while (file.exists()) {
            file = File(folder, "$base ($n)$ext")
            n++
        }
        return file
    }

    /** All sounds the user added, newest first. */
    fun library(context: Context): List<SavedSound> =
        dir(context).listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val duration = runCatching {
                    MediaMetadataRetriever().run {
                        try {
                            setDataSource(f.absolutePath)
                            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        } finally {
                            release()
                        }
                    }
                }.getOrNull()
                SavedSound(f, uriFor(context, f), f.nameWithoutExtension, f.length(), duration)
            }

    /** Deletes a sound. Only called when the user deletes it in "My sounds". */
    fun delete(context: Context, sound: SavedSound): Boolean = runCatching { sound.file.delete() }.getOrDefault(false)

    /**
     * Lets the system UI play our files as notification sounds. These grants are
     * lost on reboot, so this runs again at start-up and after every reboot.
     */
    fun grantAll(context: Context) {
        runCatching {
            dir(context).listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.forEach {
                grantToSystem(context, Uri.parse(uriFor(context, it)))
            }
        }
    }

    private fun grantToSystem(context: Context, uri: Uri) {
        runCatching { context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        runCatching { context.grantUriPermission("android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
