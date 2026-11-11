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

/** اختبار مباشر لمحرك الترجمة (بمعزل عن الشاشة) لكشف مكان المشكلة. */
object TranslationSelfTest {
    suspend fun run(target: String): String {
        val tgt = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(target)
            ?: return "لغة الهدف غير مدعومة: $target"
        val src = if (tgt == com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
            com.google.mlkit.nl.translate.TranslateLanguage.ARABIC else com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
        val sample = if (src == com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH) "Hello, how are you today?" else "مرحباً، كيف حالك اليوم؟"
        val translator = com.google.mlkit.nl.translate.Translation.getClient(
            com.google.mlkit.nl.translate.TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        return try {
            try {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            } catch (e: Exception) {
                return "❌ فشل تنزيل حزمة اللغة: ${e.message ?: e.javaClass.simpleName}\n" +
                    "تأكد من الاتصال بالإنترنت ومن وجود خدمات Google Play وتحديثها."
            }
            val out = translator.translate(sample).await()
            "✅ محرك الترجمة يعمل: «$sample» ← «$out»"
        } catch (e: Exception) {
            "❌ فشلت الترجمة: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            translator.close()
        }
    }
}
