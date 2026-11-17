package com.deepsearch.hub.search

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepsearch.hub.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** حالة شاشة البحث: المصدر (صورة/فيديو) والصورة الحالية التي سيتم البحث بها. */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    data class Source(val uri: Uri, val isVideo: Boolean, val durationMs: Long, val frameMs: Long = 0)

    private val _source = MutableStateFlow<Source?>(null)
    val source: StateFlow<Source?> = _source

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors

    /** الملف الذي يُعدّل ويُرسل للبحث. */
    val workFile: File get() = File(MediaUtils.workDir(getApplication()), "current.jpg")

    val hasImage: Boolean get() = _preview.value != null && workFile.exists()

    fun load(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        _busy.value = true
        try {
            if (MediaUtils.isVideo(ctx, uri)) {
                val duration = withContext(Dispatchers.IO) { MediaUtils.videoDurationMs(ctx, uri) }
                val start = (duration / 10).coerceAtMost(3000)
                _source.value = Source(uri, true, duration, start)
                setWorking(withContext(Dispatchers.IO) { MediaUtils.videoFrame(ctx, uri, start) })
            } else {
                _source.value = Source(uri, false, 0)
                setWorking(withContext(Dispatchers.IO) { MediaUtils.loadBitmap(ctx, uri) })
            }
        } catch (e: Exception) {
            _errors.tryEmit(e.message ?: e.javaClass.simpleName)
        } finally {
            _busy.value = false
        }
    }

    /** اختيار إطار آخر من الفيديو. */
    fun seekFrame(ms: Long) = viewModelScope.launch {
        val src = _source.value?.takeIf { it.isVideo } ?: return@launch
        _busy.value = true
        try {
            val bmp = withContext(Dispatchers.IO) { MediaUtils.videoFrame(getApplication(), src.uri, ms) }
            _source.value = src.copy(frameMs = ms)
            setWorking(bmp)
        } catch (e: Exception) {
            _errors.tryEmit(e.message ?: "Frame error")
        } finally {
            _busy.value = false
        }
    }

    /** يستقبل ناتج المحرر. */
    fun applyEdited(path: String) = viewModelScope.launch {
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } ?: return@launch
        setWorking(bmp)
    }

    private suspend fun setWorking(bmp: Bitmap) {
        withContext(Dispatchers.IO) { MediaUtils.saveJpeg(bmp, workFile) }
        _preview.value = bmp
    }
}
