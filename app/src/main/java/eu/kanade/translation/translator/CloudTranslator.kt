package eu.kanade.translation.translator

import android.text.Html
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

    /**
     * Lazily initialize the translation service on the IO thread.
     * This prevents NetworkOnMainThreadException during object construction.
     */
    private suspend fun initializeServiceIfNeeded() = withContext(Dispatchers.IO) {
        if (translateService == null) {
            if (apiKey.isBlank()) {
                val errorMsg = "CloudTranslator: API key is empty"
                logcat(LogPriority.WARN) { errorMsg }
                throw IllegalStateException("Google Cloud Translation API key is not configured. Please add your API key in Settings > Translation > Engine API Key.")
            }

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
                throw IllegalStateException("Failed to initialize Google Cloud Translation API: ${e.message}. Please verify your API key and ensure the Translation API is enabled in Google Cloud Console with billing enabled.", e)
            }
        }
    }

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        // Initialize service if needed (on IO thread)
        initializeServiceIfNeeded()

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
                                    // Build translation options
                                    val options = mutableListOf<Translate.TranslateOption>(
                                        Translate.TranslateOption.targetLanguage(toLang.code)
                                    )

                                    // Only include source language if not auto-detect
                                    if (fromLang != TextRecognizerLanguage.AUTO_DETECT) {
                                        options.add(Translate.TranslateOption.sourceLanguage(fromLang.code))
                                    }

                                    val translation = translateService!!.translate(
                                        block.text,
                                        *options.toTypedArray()
                                    )

                                    // Decode HTML entities (&#39; -> ', &quot; -> ", etc.)
                                    val translatedText = translation.translatedText ?: block.text
                                    block.translation = decodeHtmlEntities(translatedText)
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

    /**
     * Decode HTML entities in translated text.
     * Converts &#39; -> ', &quot; -> ", &amp; -> &, etc.
     */
    private fun decodeHtmlEntities(text: String): String {
        return try {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) {
                "Failed to decode HTML entities: ${e.message}"
            }
            text // Fallback to original text
        }
    }
}
