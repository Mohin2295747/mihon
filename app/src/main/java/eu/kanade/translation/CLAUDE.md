# Translation Module Documentation

## Overview

The Translation Module provides comprehensive manga/manhwa/manhua translation capabilities with support for multiple translation engines, including AI-powered and on-device translation. This module handles text recognition from manga images, translation, and rendering translated text back onto the images.

**Location**: `app/src/main/java/eu/kanade/translation/`

---

## Architecture

### Module Structure

```
eu/kanade/translation/
├── ChapterTranslator.kt           # Main translation coordinator
├── TranslationManager.kt          # High-level translation manager
├── data/
│   ├── TranslationProvider.kt     # File management for translations
│   └── TranslationFont.kt         # Font configuration
├── model/
│   ├── Translation.kt             # Translation state model
│   ├── PageTranslation.kt         # Per-page translation data
│   ├── PageTranslationHelper.kt   # Helper utilities
│   └── TranslationBlock.kt        # Text block model
├── recognizer/
│   ├── TextRecognizer.kt          # OCR coordinator
│   └── TextRecognizerLanguage.kt  # Source language enum
├── translator/
│   ├── TextTranslator.kt          # Translator interface & enum
│   ├── TextTranslatorLanguage.kt  # Target language enum
│   ├── MLKitTranslator.kt         # On-device translation
│   ├── CloudTranslator.kt         # Google Cloud Translation API
│   ├── GeminiTranslator.kt        # Gemini AI translation
│   └── OpenRouterTranslator.kt    # OpenRouter API translation
└── presentation/
    └── # UI components for translation overlay
```

---

## Translation Engines

### 1. MLKit Translator (On-Device)

**File**: `translator/MLKitTranslator.kt`

**Features**:
- Fully offline translation
- No API key required
- Fast and private
- Limited language pairs
- Lower quality for manga context

**Usage**:
```kotlin
val translator = MLKitTranslator(
    fromLang = TextRecognizerLanguage.JAPANESE,
    toLang = TextTranslatorLanguage.ENGLISH
)
translator.translate(pages)
```

**Supported Languages**: 50+ language pairs (subset of all languages)

**Best For**:
- Users without API keys
- Privacy-focused translation
- Offline translation
- Quick testing

---

### 2. Google Cloud Translation API

**File**: `translator/CloudTranslator.kt`

**Features**:
- Official Google Cloud Translation API
- High-quality neural machine translation
- Supports 100+ languages
- Requires API key with billing enabled
- BYOK (Bring Your Own Key) model

**Setup**:
1. Create Google Cloud project at https://console.cloud.google.com
2. Enable Cloud Translation API
3. Enable billing (pay-as-you-go pricing)
4. Create API key
5. Add API key in Settings > Translation > Engine API Key

**Usage**:
```kotlin
val translator = CloudTranslator(
    fromLang = TextRecognizerLanguage.JAPANESE,
    toLang = TextTranslatorLanguage.ENGLISH,
    apiKey = "your-api-key"
)
translator.translate(pages)
```

**Pricing**: ~$20 per 1 million characters
**API Limits**: 500,000 characters/month free tier

**Best For**:
- High-quality general translation
- Large volume translation
- Professional use
- Languages not well-supported by AI models

**Error Handling**:
- Invalid API key → `IllegalStateException`
- Billing not enabled → API error logged
- Rate limiting → Automatic retry with backoff
- Network errors → Exception with full stack trace

---

### 3. Gemini AI Translator

**File**: `translator/GeminiTranslator.kt`

**Features**:
- Context-aware manga/manhwa translation
- Watermark detection and removal
- Batch translation of multiple pages
- JSON-based structured translation
- Configurable model, temperature, and tokens
- Currently using Gemini 2.5 Flash (stable, production-ready)

**Setup**:
1. Get Gemini API key from https://makersuite.google.com/app/apikey
2. Add API key in Settings > Translation > Engine API Key
3. Configure model (default: gemini-2.5-flash - stable & recommended)
4. Adjust temperature (0.0-2.0, default: 1.0)
5. Set max output tokens (default: 8192)

**Usage**:
```kotlin
val translator = GeminiTranslator(
    fromLang = TextRecognizerLanguage.CHINESE,
    toLang = TextTranslatorLanguage.ENGLISH,
    apiKey = "your-api-key",
    modelName = "gemini-2.5-flash",
    maxOutputToken = 8192,
    temp = 1.0f
)
translator.translate(pages)
```

**Available Models** (as of 2025):
- `gemini-2.5-flash` - **Recommended**: Best price-performance, stable (default)
- `gemini-2.5-pro` - Highest quality with advanced reasoning
- `gemini-2.5-flash-lite` - Fastest, optimized for cost-efficiency
- `gemini-2.0-flash` - Previous generation, stable
- Preview models: `gemini-2.5-flash-preview-09-2025` (experimental, may change)

**System Prompt**:
The Gemini translator uses a specialized system prompt that:
- Understands manga/manhwa/manhua context
- Maintains JSON structure
- Removes watermarks (replaced with "RTMTH")
- Preserves cultural idioms and expressions
- Handles speech bubbles, sound effects, and narration

**Pricing** (Google AI Studio API):
- Gemini 2.5 Flash: Free tier available with generous limits
- Free tier limits: 15 requests/minute, 1500 requests/day
- Paid tier: ~$0.075 per 1M input tokens, $0.30 per 1M output tokens
- Context: Translating 100 manga pages (~10,000 tokens) costs approximately $0.001-$0.004

**Best For**:
- Manga/manhwa/manhua translation
- Context-aware translation
- Natural dialogue translation
- Watermark removal
- Batch translation

**Configuration**:
- **Temperature** (0.0-2.0): Controls randomness
  - 0.0 = Deterministic, consistent
  - 1.0 = Balanced (default)
  - 2.0 = Creative, varied
- **Max Tokens**: Maximum response length (8192 recommended)
- **Top-K**: 30 (fixed) - Sampling parameter
- **Top-P**: 0.5 (fixed) - Nucleus sampling

---

### 4. OpenRouter Translator

**File**: `translator/OpenRouterTranslator.kt`

**Features**:
- Access to multiple AI models (GPT-4, Claude, Llama, etc.)
- Similar manga-aware system prompt as Gemini
- Watermark removal
- Configurable model and parameters
- BYOK model

**Setup**:
1. Create account at https://openrouter.ai
2. Add credits to account
3. Generate API key
4. Add API key in Settings > Translation
5. Configure model name (e.g., "anthropic/claude-3-sonnet")

**Usage**:
```kotlin
val translator = OpenRouterTranslator(
    fromLang = TextRecognizerLanguage.JAPANESE,
    toLang = TextTranslatorLanguage.ENGLISH,
    apiKey = "your-openrouter-key",
    modelName = "anthropic/claude-3-sonnet",
    maxOutputToken = 8192,
    temp = 1.0f
)
translator.translate(pages)
```

**Popular Models**:
- `anthropic/claude-3-sonnet` - High quality, good for manga
- `openai/gpt-4-turbo` - Excellent quality, more expensive
- `meta-llama/llama-3-70b` - Good balance of quality and cost
- `google/gemini-pro` - Alternative to direct Gemini API

**Best For**:
- Testing different AI models
- When Gemini is unavailable in your region
- Access to latest models (GPT-4, Claude 3, etc.)
- Unified billing for multiple models

---

## Text Recognition

### MLKit Text Recognition

**File**: `recognizer/TextRecognizer.kt`

**Supported Scripts**:
- Latin script (English, Spanish, French, etc.)
- Chinese (Simplified & Traditional)
- Japanese (Hiragana, Katakana, Kanji)
- Korean (Hangul)

**Recognition Process**:
1. Image preprocessing
2. Text detection (bounding boxes)
3. Text recognition (OCR)
4. Block grouping and ordering
5. Confidence scoring

**Models**:
- `com.google.mlkit:text-recognition:16.0.1` - Latin
- `com.google.mlkit:text-recognition-chinese:16.0.1` - Chinese
- `com.google.mlkit:text-recognition-japanese:16.0.1` - Japanese
- `com.google.mlkit:text-recognition-korean:16.0.1` - Korean

---

## Adding a New Translation Engine

### Step 1: Implement TextTranslator Interface

Create a new translator class in `translator/` directory:

```kotlin
package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import logcat.LogPriority
import logcat.logcat

class MyCustomTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    // Add other parameters as needed
) : TextTranslator {

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        try {
            logcat { "MyCustomTranslator: Starting translation from ${fromLang.label} to ${toLang.label}" }

            // Initialize your translation service
            val service = initializeService(apiKey)

            var totalBlocks = 0
            var translatedBlocks = 0

            // Iterate through pages and blocks
            for ((pageKey, pageTranslation) in pages) {
                val blocks = pageTranslation.blocks
                totalBlocks += blocks.size

                for (block in blocks) {
                    try {
                        if (block.text.isNotBlank()) {
                            // Call your translation API
                            val translatedText = service.translate(
                                text = block.text,
                                from = fromLang.code,
                                to = toLang.code
                            )

                            block.translation = translatedText
                            translatedBlocks++

                            logcat { "Translated: '${block.text.take(30)}...' -> '${block.translation.take(30)}...'" }
                        }
                    } catch (e: Exception) {
                        block.translation = block.text // Fallback
                        logcat(LogPriority.ERROR) {
                            "Failed to translate block: ${e.message}"
                        }
                    }
                }
            }

            logcat { "Translation completed: $translatedBlocks/$totalBlocks blocks successful" }

        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "MyCustomTranslator error: ${e.message}\n${e.stackTraceToString()}"
            }
            throw e
        }
    }

    override fun close() {
        // Clean up resources
        logcat { "MyCustomTranslator closed" }
    }

    private fun initializeService(apiKey: String): TranslationService {
        // Initialize your translation service
        return TranslationService(apiKey)
    }
}
```

### Step 2: Add to TextTranslators Enum

Edit `translator/TextTranslator.kt`:

```kotlin
enum class TextTranslators(val label: String) {
    MLKIT("MlKit (On Device)"),
    CLOUD_TRANSLATE("Google Cloud Translation [API KEY]"),
    GEMINI("Gemini AI [API KEY]"),
    OPENROUTER("OpenRouter [API KEY]"),
    MY_CUSTOM("My Custom Translator [API KEY]");  // Add here

    fun build(
        pref: TranslationPreferences = Injekt.get(),
        fromLang: TextRecognizerLanguage = TextRecognizerLanguage.fromPref(pref.translateFromLanguage()),
        toLang: TextTranslatorLanguage = TextTranslatorLanguage.fromPref(pref.translateToLanguage())
    ): TextTranslator {
        val apiKey = pref.translationEngineApiKey().get()
        return when(this) {
            MLKIT -> MLKitTranslator(fromLang, toLang)
            CLOUD_TRANSLATE -> CloudTranslator(fromLang, toLang, apiKey)
            GEMINI -> GeminiTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            OPENROUTER -> OpenRouterTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            MY_CUSTOM -> MyCustomTranslator(fromLang, toLang, apiKey)  // Add here
        }
    }
}
```

### Step 3: Add Dependencies

If your translator requires external libraries, add them to `gradle/libs.versions.toml`:

```toml
[versions]
my-translator-version = "1.0.0"

[libraries]
my-translator = { module = "com.example:my-translator", version.ref = "my-translator-version" }
```

Then add to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.my.translator)
}
```

### Step 4: Test Your Translator

Create test cases in `app/src/test/kotlin/`:

```kotlin
class MyCustomTranslatorTest {
    @Test
    fun `test translation`() = runTest {
        val translator = MyCustomTranslator(
            fromLang = TextRecognizerLanguage.JAPANESE,
            toLang = TextTranslatorLanguage.ENGLISH,
            apiKey = "test-key"
        )

        val pages = mutableMapOf(
            "page1" to PageTranslation(/* ... */)
        )

        translator.translate(pages)

        // Assert translations
        pages["page1"]!!.blocks.forEach { block ->
            block.translation shouldNotBe ""
        }
    }
}
```

---

## Error Handling Guidelines

### Logging Standards

All translators must follow these logging standards:

```kotlin
// Info logs - Normal operations
logcat { "TranslatorName: Starting translation from $fromLang to $toLang" }
logcat { "TranslatorName: Translating ${pages.size} pages" }
logcat { "TranslatorName: Translation completed - $success/$total blocks successful" }

// Error logs - Failures and exceptions
logcat(LogPriority.ERROR) {
    "TranslatorName: API request failed with code ${response.code}"
}

logcat(LogPriority.ERROR) {
    "TranslatorName: Translation error: ${e.message}\n${e.stackTraceToString()}"
}

// Warning logs - Recoverable issues
logcat(LogPriority.WARN) {
    "TranslatorName: API key is empty, translation will fail"
}
```

### Exception Handling

```kotlin
override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
    try {
        // Main translation logic

        for ((pageKey, page) in pages) {
            try {
                // Per-page translation

                for (block in page.blocks) {
                    try {
                        // Per-block translation with fallback
                        block.translation = translateText(block.text)
                    } catch (e: Exception) {
                        // Fallback to original text
                        block.translation = block.text
                        logcat(LogPriority.ERROR) {
                            "Failed to translate block in $pageKey: ${e.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Error translating page $pageKey: ${e.message}"
                }
                // Continue with next page
            }
        }

    } catch (e: Exception) {
        // Fatal error - log and rethrow
        logcat(LogPriority.ERROR) {
            "Fatal translation error: ${e.message}\n${e.stackTraceToString()}"
        }
        throw e
    }
}
```

### User-Facing Errors

When throwing exceptions that will be shown to users:

```kotlin
throw IllegalStateException("Google Cloud Translation API is not initialized. Please check your API key.")
throw IllegalArgumentException("Invalid language pair: $fromLang to $toLang")
throw Exception("Translation API request failed: ${response.code} - ${response.message}")
```

---

## Configuration & Preferences

### Translation Preferences

**File**: `domain/src/main/java/tachiyomi/domain/translation/TranslationPreferences.kt`

```kotlin
class TranslationPreferences(private val preferenceStore: PreferenceStore) {
    // Auto-translate after download
    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)

    // Language settings
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "CHINESE")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "ENGLISH")

    // Translation engine selection (index in TextTranslators enum)
    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)

    // Engine-specific settings
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-2.0-flash-exp")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "1")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    // Display settings
    fun translationFont() = preferenceStore.getInt("translation_font", 0)
}
```

### Settings UI

**File**: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt`

The settings screen is organized into groups:

1. **General Settings**
   - Auto-translate after downloading
   - Translation font selection

2. **Language Setup**
   - Translate from language
   - Translate to language

3. **Translation Engine**
   - Engine selection dropdown
   - API key input field (shared across engines)

4. **Advanced Settings** (AI engines only)
   - Model name
   - Temperature
   - Max output tokens

---

## Data Models

### Translation

```kotlin
data class Translation(
    val manga: Manga,
    val chapter: Chapter,
) {
    enum class State {
        QUEUE,           // Waiting to start
        RECOGNIZING,     // OCR in progress
        TRANSLATING,     // Translation in progress
        EDITING,         // Manual editing
        SAVED,          // Translation saved
        ERROR           // Translation failed
    }

    var status: State = State.QUEUE
    var progress: Int = 0
}
```

### PageTranslation

```kotlin
data class PageTranslation(
    val page: ReaderPage,
    var blocks: MutableList<TranslationBlock> = mutableListOf()
) {
    fun toJson(): String
    fun isEmpty(): Boolean
}
```

### TranslationBlock

```kotlin
data class TranslationBlock(
    val boundingBox: Rect,
    val text: String,
    var translation: String = "",
    val confidence: Float = 0f
)
```

---

## Translation Workflow

### Complete Translation Flow

1. **User Triggers Translation**
   - Manual trigger from reader menu
   - Or auto-translate after download

2. **ChapterTranslator.translateChapter()**
   - Set status to `QUEUE`
   - Load chapter pages
   - Initialize text recognizer

3. **Text Recognition** (`RECOGNIZING`)
   - For each page image:
     - Run MLKit text recognition
     - Extract text blocks with bounding boxes
     - Store as `PageTranslation` objects

4. **Translation** (`TRANSLATING`)
   - Build translator based on selected engine
   - Call `translator.translate(pages)`
   - Engine-specific translation logic
   - Update progress

5. **Save Results** (`SAVED`)
   - Serialize `PageTranslation` to JSON
   - Save to storage: `translations/{source}/{manga}/{chapter}.json`
   - Update UI

6. **Display**
   - Reader loads translation JSON
   - Renders translated text overlay
   - User can edit translations

### Error Flow

```
QUEUE → RECOGNIZING → ERROR
              ↓
         TRANSLATING → ERROR
              ↓
            SAVED
```

On error:
- Status set to `ERROR`
- Error logged with full stack trace
- User notified with error message
- Partial translations preserved

---

## Storage Format

### Translation JSON Structure

```json
{
  "page_0": {
    "blocks": [
      {
        "boundingBox": {"left": 100, "top": 50, "right": 200, "bottom": 100},
        "text": "こんにちは",
        "translation": "Hello",
        "confidence": 0.95
      }
    ]
  },
  "page_1": {
    "blocks": [...]
  }
}
```

### Storage Location

```
{storage}/translations/
  └── {source_name}/
      └── {manga_title}/
          └── {chapter_name}.json
```

Example:
```
/storage/emulated/0/Tachiyomi/translations/
  └── MangaDex/
      └── One Piece/
          └── Chapter 1.json
```

---

## Performance Considerations

### Optimization Tips

1. **Batch Translation**
   - Gemini and OpenRouter support batch translation
   - Send multiple pages in single API call
   - Reduces API overhead and cost

2. **Caching**
   - Translation results are cached to storage
   - Re-translation only if cache is deleted
   - No need to translate same chapter twice

3. **Parallel Processing**
   - Text recognition can run in parallel for multiple pages
   - Translation engines handle batching internally

4. **Rate Limiting**
   - Implement backoff for API rate limits
   - Queue translations to avoid overwhelming APIs
   - Add delays between requests if needed

### Memory Management

```kotlin
override fun close() {
    // Always clean up resources
    translator?.close()
    recognizer?.close()
    imageCache.clear()
}
```

---

## Testing Translation Engines

### Manual Testing Checklist

- [ ] Engine initializes without errors
- [ ] API key validation works
- [ ] Translation produces expected output
- [ ] Error handling works (invalid key, network error)
- [ ] Logging is comprehensive and helpful
- [ ] Watermark removal works (if applicable)
- [ ] Multiple pages translate correctly
- [ ] Progress updates accurately
- [ ] Resources are cleaned up properly
- [ ] Works with different language pairs

### Debug Logging

Enable verbose logging to debug translation issues:

```kotlin
// In ChapterTranslator.kt
logcat(LogPriority.DEBUG) { "Recognized text: ${blocks.joinToString()}" }
logcat(LogPriority.DEBUG) { "Translation input: $inputJson" }
logcat(LogPriority.DEBUG) { "Translation output: $outputJson" }
```

View logs:
```bash
adb logcat | grep -E "(MLKitTranslator|CloudTranslator|GeminiTranslator|OpenRouterTranslator)"
```

---

## Troubleshooting

### Common Issues

**Issue**: "API key is not initialized"
- Check API key is entered in settings
- Verify API key format is correct
- Check logs for initialization errors

**Issue**: "Translation returns empty"
- Enable debug logging
- Check API response in logs
- Verify language pair is supported
- Check API quota/billing

**Issue**: "Out of memory during translation"
- Reduce batch size
- Lower image quality for recognition
- Process fewer pages at once

**Issue**: "Watermarks not removed"
- Check watermark detection regex
- Verify "RTMTH" replacement logic
- Review system prompt for AI engines

---

## Future Improvements

### Planned Features

1. **Translation Memory**
   - Store common phrase translations
   - Reuse translations across chapters
   - User-editable glossary

2. **Custom Models**
   - Fine-tuned models for manga/manhwa
   - Language-specific models
   - Genre-specific translation styles

3. **Collaborative Translation**
   - Share translations between users
   - Community-reviewed translations
   - Translation voting system

4. **Advanced OCR**
   - Vertical text support
   - Curved text handling
   - Sound effect translation
   - Text region detection improvement

---

## API References

### TextTranslator Interface

```kotlin
interface TextTranslator : Closeable {
    val fromLang: TextRecognizerLanguage
    val toLang: TextTranslatorLanguage
    suspend fun translate(pages: MutableMap<String, PageTranslation>)
    override fun close()
}
```

### Key Extension Functions

```kotlin
// Convert language code to MLKit format
fun String.toMLKitLanguage(): String

// Batch pages for optimal API usage
fun Map<String, PageTranslation>.batch(size: Int): List<Map<String, PageTranslation>>

// Sanitize text for translation
fun String.sanitizeForTranslation(): String
```

---

## Contact & Support

For translation module specific issues:
1. Check logs for detailed error messages
2. Verify API keys and configurations
3. Test with different engines to isolate issues
4. Review this documentation for best practices
5. Check GitHub issues for similar problems

When reporting issues, include:
- Translation engine used
- Source and target languages
- Error logs (with sensitive data removed)
- Steps to reproduce
- Expected vs actual behavior
