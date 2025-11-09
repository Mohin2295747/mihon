package eu.kanade.translation.translator

import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

/**
 * Google Cloud Translation API translator with BYOK (Bring Your Own Key) model.
 *
 * Requires a valid Google Cloud API key with Translation API enabled.
 * Users must enable billing on their Google Cloud account.
 *
 * API Documentation: https://cloud.google.com/translate/docs
 */
class CloudTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
) : TextTranslator {

    private var translateService: Translate? = null

    init {
        if (apiKey.isNotBlank()) {
            try {
                translateService = TranslateOptions.newBuilder()
                    .setApiKey(apiKey)
                    .build()
                    .service
                logcat { "CloudTranslator initialized successfully" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to initialize CloudTranslator: ${e.message}\n${e.stackTraceToString()}"
                }
            }
        } else {
            logcat(LogPriority.WARN) { "CloudTranslator: API key is empty" }
        }
    }

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        if (translateService == null) {
            val errorMsg = "Google Cloud Translation API is not initialized. Please check your API key."
            logcat(LogPriority.ERROR) { errorMsg }
            throw IllegalStateException(errorMsg)
        }

        try {
            logcat { "Starting Cloud Translation: ${pages.size} pages, from ${fromLang.label} to ${toLang.label}" }

            withContext(Dispatchers.IO) {
                var totalBlocks = 0
                var translatedBlocks = 0
                var failedBlocks = 0

                for ((pageKey, pageTranslation) in pages) {
                    try {
                        val blocks = pageTranslation.blocks
                        totalBlocks += blocks.size

                        logcat { "Translating page $pageKey: ${blocks.size} blocks" }

                        // Translate each block
                        for (block in blocks) {
                            try {
                                if (block.text.isNotBlank()) {
                                    val translation = translateService!!.translate(
                                        block.text,
                                        Translate.TranslateOption.sourceLanguage(fromLang.code),
                                        Translate.TranslateOption.targetLanguage(toLang.code)
                                    )

                                    block.translation = translation.translatedText ?: block.text
                                    translatedBlocks++

                                    logcat { "Translated block: '${block.text.take(30)}...' -> '${block.translation.take(30)}...'" }
                                } else {
                                    block.translation = ""
                                    logcat { "Skipped empty block" }
                                }
                            } catch (e: Exception) {
                                block.translation = block.text // Fallback to original text
                                failedBlocks++
                                logcat(LogPriority.ERROR) {
                                    "Failed to translate block in page $pageKey: ${e.message}"
                                }
                            }
                        }

                        // Remove watermark blocks (blocks containing "RTMTH" or common watermark patterns)
                        pageTranslation.blocks = blocks.filterNot {
                            it.translation.contains("RTMTH", ignoreCase = true) ||
                            it.translation.matches(Regex(".*\\.(com|org|net|io).*", RegexOption.IGNORE_CASE))
                        }.toMutableList()

                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR) {
                            "Error translating page $pageKey: ${e.message}\n${e.stackTraceToString()}"
                        }
                        throw e
                    }
                }

                logcat {
                    "Cloud Translation completed: $translatedBlocks/$totalBlocks blocks successful, $failedBlocks failed"
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Cloud Translation Error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
        translateService = null
        logcat { "CloudTranslator closed" }
    }
}
