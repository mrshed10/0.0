package com.deepsearch.hub.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object MediaUtils {

    fun workDir(context: Context): File = File(context.cacheDir, "work").apply { mkdirs() }

    fun isVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video") == true

    /** يحمّل صورة مع تصغيرها إلى maxDim وتصحيح الاتجاه (EXIF). */
    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = max(w, h).toFloat() / maxDim
                if (scale > 1f) decoder.setTargetSize((w / scale).roundToInt(), (h / scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        }
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }
        val bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Unsupported image")
        val rotation = cr.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        return scaleDown(rotate(bmp, rotation), maxDim)
    }

    fun videoDurationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    }

    /** يستخرج إطاراً من الفيديو عند الزمن المحدد. */
    fun videoFrame(context: Context, uri: Uri, timeMs: Long, maxDim: Int = 1920): Bitmap {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val us = timeMs * 1000
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, maxDim, maxDim)
            } else {
                r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)?.let { scaleDown(it, maxDim) }
            }
            return frame ?: error("Could not read video frame")
        } finally {
            r.release()
        }
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** يجهز JPEG صغيراً للرفع (أسرع وأقل استهلاكاً، وكافٍ لكل محركات البحث). */
    fun compressForUpload(file: File, maxDim: Int = 1600): ByteArray {
        val bmp = scaleDown(BitmapFactory.decodeFile(file.path) ?: error("Bad image"), maxDim)
        return ByteArrayOutputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, it)
            it.toByteArray()
        }
    }

    fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxDim) return bmp
        val s = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
    }

    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun flipHorizontal(bmp: Bitmap): Bitmap {
        val m = Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun formatTime(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
