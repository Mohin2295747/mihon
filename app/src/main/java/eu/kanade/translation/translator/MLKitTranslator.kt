package eu.kanade.translation.translator

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import logcat.LogPriority
import logcat.logcat

class MLKitTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(fromLang.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.ENGLISH)
            .build(),
    )

    private var conditions = DownloadConditions.Builder().build()

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        try {
            logcat { "MLKitTranslator: Starting translation from ${fromLang.label} to ${toLang.label}" }
            logcat { "MLKitTranslator: Downloading model if needed..." }

            Tasks.await(translator.downloadModelIfNeeded(conditions))

            logcat { "MLKitTranslator: Model ready, translating ${pages.size} pages" }

            var totalBlocks = 0
            var translatedBlocks = 0

            pages.mapValues { (pageKey, v) ->
                try {
                    totalBlocks += v.blocks.size
                    v.blocks.map { b ->
                        try {
                            b.translation = b.text.split("\n").mapNotNull {
                                Tasks.await(translator.translate(it)).takeIf { it.isNotEmpty() }
                            }.joinToString("\n")
                            translatedBlocks++
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR) {
                                "MLKitTranslator: Failed to translate block in page $pageKey: ${e.message}"
                            }
                            b.translation = b.text // Fallback to original text
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) {
                        "MLKitTranslator: Error translating page $pageKey: ${e.message}"
                    }
                }
            }

            logcat { "MLKitTranslator: Translation completed - $translatedBlocks/$totalBlocks blocks successful" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "MLKitTranslator: Translation error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
        translator.close()
        logcat { "MLKitTranslator: Translator closed" }
    }
}
