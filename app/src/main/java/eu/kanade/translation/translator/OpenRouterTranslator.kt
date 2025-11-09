package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenRouterTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKey: String,
    val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {
    private val okHttpClient = OkHttpClient()
    
    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        try {
            logcat { "OpenRouterTranslator: Starting translation from ${fromLang.label} to ${toLang.label}" }
            logcat { "OpenRouterTranslator: Using model $modelName with temp=$temp, maxTokens=$maxOutputToken" }

            val data = pages.mapValues { (k, v) -> v.blocks.map { b -> b.text } }
            val json = JSONObject(data)

            logcat { "OpenRouterTranslator: Sending ${pages.size} pages with ${data.values.sumOf { it.size }} blocks to API" }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val jsonObject = buildJsonObject {
                put("model", modelName)
                putJsonObject("response_format") { put("type", "json_object") }
                put("top_p", 0.5f)
                put("top_k", 30)
                put("temperature", temp)
                put("max_tokens", maxOutputToken)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", buildSystemInstruction())
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", "JSON $json")
                    }
                }
            }.toString()
            
            val body = jsonObject.toRequestBody(mediaType)
            val access = "https://openrouter.ai/api/v1/chat/completions"
            val build: Request = Request.Builder().url(access)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
                
            val response = okHttpClient.newCall(build).await()
            val rBody = response.body

            logcat { "OpenRouterTranslator: Received response from OpenRouter API (HTTP ${response.code})" }

            if (!response.isSuccessful) {
                logcat(LogPriority.ERROR) {
                    "OpenRouterTranslator: API request failed with code ${response.code}: ${rBody?.string()}"
                }
                throw Exception("OpenRouter API request failed: ${response.code}")
            }

            val json2 = JSONObject(rBody.string())
            val resJson = JSONObject(
                json2.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            )

            var totalBlocks = 0
            var translatedBlocks = 0
            var removedWatermarks = 0

            for ((k, v) in pages) {
                totalBlocks += v.blocks.size
                v.blocks.forEachIndexed { i, b ->
                    run {
                        val res = resJson.optJSONArray(k)?.optString(i, "NULL")
                        b.translation = if (res == null || res == "NULL") b.text else res
                        if (res != null && res != "NULL") translatedBlocks++
                    }
                }
<<<<<<< HEAD
                v.blocks = v.blocks.filterNot { 
                    it.translation.contains("RTMTH") 
                }.toMutableList()
            }
=======
                val originalSize = v.blocks.size
                v.blocks =
                    v.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
                removedWatermarks += (originalSize - v.blocks.size)
            }

            logcat {
                "OpenRouterTranslator: Translation completed - $translatedBlocks/$totalBlocks blocks, " +
                    "removed $removedWatermarks watermark blocks"
            }

>>>>>>> 029632052 (Upgrade translation system with Gemini 2.5 Flash and Google Cloud Translation API)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "OpenRouterTranslator: Translation error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    private fun buildSystemInstruction(): String {
        return "## System Prompt for Manhwa/Manga/Manhua Translation\n" +
            "\n" +
            "You are a highly skilled AI tasked with translating text from scanned images " +
            "of comics (manhwa, manga, manhua) while preserving the original structure and " +
            "removing any watermarks or site links.\n" +
            "\n" +
            "**Here's how you should operate:**\n" +
            "\n" +
            "1. **Input:** You'll receive a JSON object where keys are image filenames " +
            "(e.g., \"001.jpg\") and values are lists of text strings extracted from those images.\n" +
            "\n" +
            "2. **Translation:** Translate all text strings to the target language " +
            "${toLang.label}. Ensure the translation is natural and fluent, adapting idioms " +
            "and expressions to fit the target language's cultural context.\n" +
            "\n" +
            "3. **Watermark/Site Link Removal:** Replace any watermarks or site links " +
            "(e.g., \"colamanga.com\") with the placeholder \"RTMTH\".\n" +
            "\n" +
            "4. **Structure Preservation:** Maintain the exact same structure as the input JSON. " +
            "The output JSON should have the same number of keys (image filenames) and the same " +
            "number of text strings within each list.\n" +
            "\n" +
            "**Example:**\n" +
            "\n" +
            "**Input:**\n" +
            "\n" +
            "```json\n" +
            "{\"001.jpg\":[\"chinese1\",\"chinese2\"],\"002.jpg\":[\"chinese2\",\"colamanga.com\"]}\n" +
            "```\n" +
            "\n" +
            "**Output (for ${toLang.label} = English):**\n" +
            "\n" +
            "```json\n" +
            "{\"001.jpg\":[\"eng1\",\"eng2\"],\"002.jpg\":[\"eng2\",\"RTMTH\"]}\n" +
            "```\n" +
            "\n" +
            "**Key Points:**\n" +
            "\n" +
            "* Prioritize accurate and natural-sounding translations.\n" +
            "* Be meticulous in removing all watermarks and site links.\n" +
            "* Ensure the output JSON structure perfectly mirrors the input structure."
    }

    override fun close() {
    }
}
