package com.livetranslate.screen.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** إدارة حزم اللغات للاستخدام دون إنترنت (Offline Translation Packs). */
object LanguagePacks {

    private val manager get() = RemoteModelManager.getInstance()

    val allLanguages: List<String> get() = TranslateLanguage.getAllLanguages().sortedBy { displayName(it) }

    fun displayName(code: String): String {
        if (code == "auto") return "اكتشاف تلقائي"
        val name = Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag("ar"))
        return if (name.isBlank() || name == code) code else name
    }

    suspend fun downloaded(): Set<String> =
        runCatching { manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet() }
            .getOrDefault(emptySet())

    suspend fun download(code: String, wifiOnly: Boolean): Boolean {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        return runCatching {
            manager.download(TranslateRemoteModel.Builder(code).build(), conditions).await(); true
        }.getOrDefault(false)
    }

    suspend fun delete(code: String): Boolean = runCatching {
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(code).build()).await(); true
    }.getOrDefault(false)
}
