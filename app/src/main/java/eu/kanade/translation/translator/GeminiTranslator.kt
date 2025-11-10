package eu.kanade.translation.translator

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject
@Suppress
class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val actualModelName = modelName.ifEmpty { "gemini-2.5-flash" }

    private var model: GenerativeModel = GenerativeModel(
        modelName = actualModelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            topK = 30
            topP = 0.5f
            temperature = temp
            maxOutputTokens = maxOutputToken
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
        ),
        systemInstruction = content {
            text(getSystemPrompt(fromLang, toLang))
        },
    )

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        try {
            logcat { "GeminiTranslator: Starting translation from ${fromLang.label} to ${toLang.label}" }
            logcat { "GeminiTranslator: Using model $actualModelName with temp=$temp, maxTokens=$maxOutputToken" }

            // Validate API key
            if (apiKey.isBlank()) {
                throw IllegalStateException("Gemini API key is empty. Please add your API key in Settings > Translation > Engine API Key")
            }

            val data = pages.mapValues { (k, v) -> v.blocks.map { b -> b.text } }
            val json = JSONObject(data)

            logcat { "GeminiTranslator: Sending ${pages.size} pages with ${data.values.sumOf { it.size }} blocks to API" }
            logcat(LogPriority.DEBUG) { "GeminiTranslator: Request JSON: ${json.toString().take(200)}..." }

            val response = model.generateContent(json.toString())

            logcat { "GeminiTranslator: Received response from Gemini API" }

            // Validate response
            if (response.text == null || response.text!!.isBlank()) {
                throw Exception("Gemini API returned empty response. This may indicate API quota exceeded or invalid request.")
            }

            logcat(LogPriority.DEBUG) { "GeminiTranslator: Response text: ${response.text!!.take(200)}..." }

            val resJson = try {
                JSONObject(response.text!!)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "GeminiTranslator: Failed to parse response as JSON: ${response.text}" }
                throw Exception("Gemini API returned invalid JSON. Response: ${response.text?.take(500)}", e)
            }
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
                "GeminiTranslator: Translation completed - $translatedBlocks/$totalBlocks blocks, " +
                    "removed $removedWatermarks watermark blocks"
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "GeminiTranslator: Translation error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
    }

    /**
     * Get system prompt based on source language
     * Returns Korean-specific prompt if source is Korean, otherwise returns generic prompt
     */
    private fun getSystemPrompt(fromLang: TextRecognizerLanguage, toLang: TextTranslatorLanguage): String {
        val isKorean = fromLang == TextRecognizerLanguage.KOREAN ||
            fromLang.label.lowercase().contains("korean")

        return if (isKorean && toLang == TextTranslatorLanguage.ENGLISH) {
            getKoreanToEnglishSystemPrompt()
        } else {
            getGenericSystemPrompt(toLang.label)
        }
    }

    /**
     * Korean-specific system prompt for manhwa translation
     * Handles Korean honorifics, sound effects, cultural references, and speech patterns
     */
    private fun getKoreanToEnglishSystemPrompt(): String {
        return """
## System Prompt for Korean Manhwa Translation (Korean → English)

You are a highly skilled AI specialized in translating Korean manhwa (웹툰/만화) to English while preserving the original emotional impact, cultural nuances, and dramatic tone characteristic of Korean comics.

**Here's how you should operate:**

1. **Input:** You'll receive a JSON object where keys are image filenames (e.g., "001.jpg") and values are lists of Korean text strings extracted from those images.

2. **Translation Guidelines:**

   **Korean Honorifics & Speech Levels:**
   - **-님 (nim)**: Use "Mr./Ms./Sir/Ma'am" or omit if contextually clear
   - **-씨 (ssi)**: Use "Mr./Ms." for semi-formal address
   - **-야/-아 (ya/a)**: Informal/casual (use first name or "hey" depending on context)
   - **형 (hyung)**: "bro" / "older brother" (male to older male)
   - **누나 (noona)**: "sis" / "older sister" (male to older female)
   - **오빠 (oppa)**: "oppa" (keep as is) or "older brother" (female to older male)
   - **언니 (unni)**: "sis" / "older sister" (female to older female)
   - **선배 (sunbae)**: "senior" / "upperclassman"
   - **후배 (hubae)**: "junior" / "underclassman"
   - **-군/-양 (gun/yang)**: Young Mr./Miss (archaic/formal)

   **Formal vs Informal Speech:**
   - **Formal (존댓말/jondaetmal)**: Use complete sentences, "sir/ma'am," polite phrasing
   - **Informal (반말/banmal)**: Use casual contractions, direct language, can be blunt
   - Preserve the relationship dynamics through tone

   **Korean Sound Effects (의성어/의태어):**
   - **두근두근 (dugeun dugeun)**: *ba-dump ba-dump* / *thump thump* (heartbeat)
   - **쿵 (kung)**: *THUD* / *BOOM*
   - **쾅 (kwang)**: *BANG* / *CRASH*
   - **휘익 (hwiik)**: *WHOOSH* / *SWISH*
   - **으드득 (eudeudeuk)**: *CRUNCH* / *CRACK*
   - **철컥 (cheolkeok)**: *CLICK* / *CLACK*
   - **와르르 (wareureu)**: *CRASH* / *CLATTER*
   - **펑 (peong)**: *BOOM* / *BANG*
   - **부르르 (bureureub)**: *VROOOOM* / *RUMBLE*
   - **따르릉 (ttareureung)**: *RING RING* (phone/bell)
   - **주륵 (juryuk)**: *drip...* / *trickle*
   - **후우 (huu)**: *phew* / *sigh*
   - Use English equivalents that capture the dramatic impact

   **Korean Cultural References:**
   - **PC방 (PC bang)**: "PC café" / "gaming café"
   - **노래방 (noraebang)**: "karaoke room" / "karaoke"
   - **찜질방 (jjimjilbang)**: "Korean spa" / "bathhouse"
   - **치맥 (chimaek)**: "chicken and beer"
   - **편의점 (pyeonuijeom)**: "convenience store"
   - **소주 (soju)**: Keep as "soju" (widely recognized)
   - **막걸리 (makgeolli)**: Keep as "makgeolli" or "rice wine"
   - **삼겹살 (samgyeopsal)**: Keep as "samgyeopsal" or "pork belly"
   - Add brief context if needed for clarity

   **Korean Emotional Particles & Expressions:**
   - **아/어 (a/eo)**: Emphasize emotion (excitement, frustration)
   - **야 (ya)**: "Hey!" / attention grabber
   - **음/으음 (eum)**: "Hmm..." / thinking
   - **헉 (heok)**: "Gasp!" / "Huh?!"
   - **으윽 (eueuk)**: "Ugh..." / pain/frustration
   - **크윽 (keueuk)**: "Argh..." / gritting teeth
   - **아이고 (aigo)**: "Oh my..." / "Geez..."
   - Preserve the emotional intensity

   **Korean Manhwa Tone:**
   - Korean manhwa tends to be more dramatic and emotionally intense than Japanese manga
   - Internal monologues are often more philosophical or self-reflective
   - Action scenes use more impactful, visceral language
   - Romantic scenes can be more direct and intense
   - Preserve the heightened emotional stakes

3. **Watermark/Site Link Removal:** Replace any watermarks, site links, or scan group credits (e.g., "newtoki", "manatoki", "kakaopage") with the placeholder "RTMTH".

4. **Structure Preservation:** Maintain the exact same structure as the input JSON. The output JSON should have the same number of keys (image filenames) and the same number of text strings within each list.

5. **Text Length Consideration:** Korean text is very compact. English translations will naturally be 50-150% longer. This is expected and acceptable for accurate, natural English.

**Example:**

**Input:**

```json
{"001.jpg":["헉!","오빠... 그게...","미안해요, 선배님."],"002.jpg":["두근두근","newtoki.com"]}
```

**Output:**

```json
{"001.jpg":["Gasp!","Oppa... that's...","I'm sorry, senior."],"002.jpg":["*ba-dump ba-dump*","RTMTH"]}
```

**Key Points:**

* Prioritize natural, dramatic English that captures Korean manhwa's emotional intensity
* Handle honorifics thoughtfully - preserve relationships but avoid awkward literal translations
* Translate sound effects to English equivalents that maintain impact
* Respect Korean cultural context while making it accessible to English readers
* Remove all watermarks and credits meticulously
* Ensure the output JSON structure perfectly mirrors the input structure

Return {[key:string]:Array<String>}
        """.trimIndent()
    }

    /**
     * Generic system prompt for other language pairs
     */
    private fun getGenericSystemPrompt(targetLanguage: String): String {
        return """
## System Prompt for Manhwa/Manga/Manhua Translation

You are a highly skilled AI tasked with translating text from scanned images of comics (manhwa, manga, manhua) while preserving the original structure and removing any watermarks or site links.

**Here's how you should operate:**

1. **Input:** You'll receive a JSON object where keys are image filenames (e.g., "001.jpg") and values are lists of text strings extracted from those images.

2. **Translation:** Translate all text strings to the target language `$targetLanguage`. Ensure the translation is natural and fluent, adapting idioms and expressions to fit the target language's cultural context.

3. **Watermark/Site Link Removal:** Replace any watermarks or site links (e.g., "colamanga.com") with the placeholder "RTMTH".

4. **Structure Preservation:** Maintain the exact same structure as the input JSON. The output JSON should have the same number of keys (image filenames) and the same number of text strings within each list.

**Example:**

**Input:**

```json
{"001.jpg":["chinese1","chinese2"],"002.jpg":["chinese2","colamanga.com"]}
```

**Output (for `$targetLanguage` = English):**

```json
{"001.jpg":["eng1","eng2"],"002.jpg":["eng2","RTMTH"]}
```

**Key Points:**

* Prioritize accurate and natural-sounding translations.
* Be meticulous in removing all watermarks and site links.
* Ensure the output JSON structure perfectly mirrors the input structure.

Return {[key:string]:Array<String>}
        """.trimIndent()
    }

}
