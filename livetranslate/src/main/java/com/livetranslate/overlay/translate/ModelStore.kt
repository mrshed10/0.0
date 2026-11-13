package com.livetranslate.overlay.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * إدارة حزم لغات الترجمة.
 * تُنزَّل الحزم مسبقاً (من شاشة التطبيق أو قبل بدء الجلسة)، ولا يحاول المحرك أبداً
 * تنزيل حزمة لغة لاتينية أثناء الترجمة: لغة اكتُشفت خطأً (hr/vi مثلاً) لا تسبب أي تنزيل.
 * الاستثناء الوحيد: لغة مؤكدة من نظام كتابتها تُنزَّل بصمت في الخلفية ([requestInBackground]).
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

    /**
     * ينزّل الحزم الناقصة من [codes] (تجاهل "auto" والإنجليزية المضمّنة).
     * لا يتوقف عند أول فشل؛ يُرجع رموز اللغات التي فشل تنزيلها.
     */
    suspend fun ensure(codes: Collection<String>): List<String> = mutex.withLock {
        runCatching { refresh() }
        val failed = mutableListOf<String>()
        for (code in codes.distinct()) {
            val tag = tagOf(code) ?: continue
            if (isReady(tag)) continue
            _downloading.value = code
            try {
                manager.download(
                    TranslateRemoteModel.Builder(tag).build(),
                    DownloadConditions.Builder().build(),
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += code
            } finally {
                _downloading.value = null
            }
        }
        runCatching { refresh() }
        failed
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = mutableSetOf<String>()
    private val failedAt = mutableMapOf<String, Long>()

    /**
     * تنزيل صامت في الخلفية للغة تأكدت من نظام كتابتها (كورية/يابانية/صينية/هندية) ولم تُنزَّل حزمتها.
     * لا يظهر أي شيء فوق الشاشة؛ وعند اكتماله يُعاد ترجمة الشاشة تلقائياً.
     * يُستدعى على الخيط الرئيسي.
     */
    fun requestInBackground(code: String) {
        val tag = tagOf(code) ?: return
        if (isReady(tag) || tag in pending) return
        val lastFail = failedAt[tag]
        if (lastFail != null && SystemClock.elapsedRealtime() - lastFail < RETRY_AFTER_MS) return
        pending += tag
        backgroundScope.launch {
            try {
                if (ensure(listOf(tag)).isNotEmpty()) failedAt[tag] = SystemClock.elapsedRealtime()
            } finally {
                pending -= tag
            }
        }
    }

    private const val RETRY_AFTER_MS = 60_000L

    suspend fun delete(code: String) = mutex.withLock {
        val tag = tagOf(code) ?: return@withLock
        if (tag == TranslateLanguage.ENGLISH) return@withLock
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await()
        refresh()
    }
}
