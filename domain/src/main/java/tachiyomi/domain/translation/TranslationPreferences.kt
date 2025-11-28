package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "AUTO_DETECT")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "ENGLISH")
    fun translationFont() = preferenceStore.getInt("translation_font", 0)

    // Legacy preference - kept for backwards compatibility, maps to CJK margin
    fun translationTextMargin() = preferenceStore.getInt("translation_text_margin", 2)

    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-2.5-flash")
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "1")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    // Language detection method for Google Cloud Translation AUTO mode
    // "mlkit" = Use MLKit's OCR-based language detection (default)
    // "google_cloud" = Use Google Cloud Translation's auto-detection
    fun translationLanguageDetectionMethod() = preferenceStore.getString("translation_language_detection_method", "mlkit")

    // ============================================================================
    // CJK Display Settings (Korean/Japanese/Chinese)
    // These preserve existing behavior as defaults
    // ============================================================================

    // Text margin from bubble edges (0-8px)
    fun cjkTextMargin() = preferenceStore.getInt("cjk_text_margin", 2)

    // Padding multiplier for symbol dimensions (100-150%)
    // Korean: 115%, Japanese: 120%, Chinese: 110%
    fun cjkPaddingMultiplier() = preferenceStore.getInt("cjk_padding_multiplier", 115)

    // Oval height usage - percentage of bubble height for text area (50-95%)
    // Conservative 70% to avoid curved edges in horizontal ovals
    fun cjkOvalHeightMargin() = preferenceStore.getInt("cjk_oval_height_margin", 70)

    // ============================================================================
    // Latin Display Settings (Spanish/Indonesian)
    // These fix text clipping issues with more generous margins
    // ============================================================================

    // Text margin from bubble edges (0-8px)
    fun latinTextMargin() = preferenceStore.getInt("latin_text_margin", 2)

    // Padding multiplier for symbol dimensions (100-150%)
    // Latin uses 100% (no extra padding needed)
    fun latinPaddingMultiplier() = preferenceStore.getInt("latin_padding_multiplier", 100)

    // Oval height usage - percentage of bubble height for text area (70-99%)
    // Generous 95% to prevent text clipping
    fun latinOvalHeightMargin() = preferenceStore.getInt("latin_oval_height_margin", 95)

    // Horizontal padding in dp (2-12dp)
    fun latinHorizontalPadding() = preferenceStore.getInt("latin_horizontal_padding", 6)

    // Vertical padding in dp (2-10dp)
    fun latinVerticalPadding() = preferenceStore.getInt("latin_vertical_padding", 4)
}
