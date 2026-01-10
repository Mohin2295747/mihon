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
                        put("content", TranslationPrompts.forLanguagePair(fromLang, toLang))
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", "JSON $json")
                    }
                }

            }.toString()
            val body = jsonObject.toRequestBody(mediaType)
            val access = "https://openrouter.ai/api/v1/chat/completions"
            val build: Request =
                Request.Builder().url(access).header(
                    "Authorization",
                    "Bearer $apiKey",
                ).header("Content-Type", "application/json").post(body).build()
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
            val resJson =
                JSONObject(json2.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))

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
                val originalSize = v.blocks.size
                v.blocks =
                    v.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
                removedWatermarks += (originalSize - v.blocks.size)
            }

            logcat {
                "OpenRouterTranslator: Translation completed - $translatedBlocks/$totalBlocks blocks, " +
                    "removed $removedWatermarks watermark blocks"
            }

        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "OpenRouterTranslator: Translation error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
    }


}
