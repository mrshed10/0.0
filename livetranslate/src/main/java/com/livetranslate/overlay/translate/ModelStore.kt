package com.livetranslate.overlay.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * إدارة حزم لغات الترجمة.
 * تُنزَّل الحزم مسبقاً (من شاشة التطبيق أو قبل بدء الجلسة)، ولا يحاول المحرك أبداً
 * تنزيل حزمة أثناء الترجمة: لغة اكتُشفت خطأً (hr/vi مثلاً) لا تسبب أي تنزيل.
 */
object ModelStore {
    private val manager = RemoteModelManager.getInstance()
    private val mutex = Mutex()

    private val _downloaded = MutableStateFlow(setOf(TranslateLanguage.ENGLISH))
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    /** الحزمة التي يجري تنزيلها الآن (أو null). */
    private val _downloading = MutableStateFlow<String?>(null)
    val downloading: StateFlow<String?> = _downloading.asStateFlow()

    fun tagOf(code: String): String? = TranslateLanguage.fromLanguageTag(code)

    fun isReady(code: String): Boolean {
        val tag = tagOf(code) ?: return false
        return tag == TranslateLanguage.ENGLISH || tag in _downloaded.value
    }

    suspend fun refresh(): Set<String> {
        val models = manager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        val set = models.map { it.language }.toSet() + TranslateLanguage.ENGLISH
        _downloaded.value = set
        return set
    }

    /** ينزّل الحزم الناقصة من [codes] (تجاهل "auto" والإنجليزية المضمّنة). */
    suspend fun ensure(codes: Collection<String>) = mutex.withLock {
        runCatching { refresh() }
        for (code in codes.distinct()) {
            val tag = tagOf(code) ?: continue
            if (isReady(tag)) continue
            _downloading.value = code
            try {
                manager.download(
                    TranslateRemoteModel.Builder(tag).build(),
                    DownloadConditions.Builder().build(),
                ).await()
            } finally {
                _downloading.value = null
            }
        }
        refresh()
    }

    suspend fun delete(code: String) = mutex.withLock {
        val tag = tagOf(code) ?: return@withLock
        if (tag == TranslateLanguage.ENGLISH) return@withLock
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await()
        refresh()
    }
}
