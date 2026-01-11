package tachiyomi.domain.translation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.domain.translation.model.TranslationProfile

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

    // ============================================================================
    // Profile Factory Methods
    // ============================================================================

    /**
     * Get the current CJK translation profile as a snapshot.
     */
    fun getCJKProfile(): TranslationProfile {
        return TranslationProfile(
            type = ProfileType.CJK,
            textMargin = cjkTextMargin().get(),
            paddingMultiplier = cjkPaddingMultiplier().get() / 100f,
            ovalHeightMargin = cjkOvalHeightMargin().get() / 100f,
            horizontalPadding = 8, // CJK uses fixed padding
            verticalPadding = 2,   // CJK uses fixed padding
            minFontSize = 12,      // CJK uses fixed font size
            maxFontSize = 12,      // CJK uses fixed font size
            lineHeightMultiplier = 1.4f,
            minExpansion = 1.8f,
            maxExpansion = 2.5f,
            useHybridOverflow = false,
        )
    }

    /**
     * Get the current Latin translation profile as a snapshot.
     */
    fun getLatinProfile(): TranslationProfile {
        return TranslationProfile(
            type = ProfileType.LATIN,
            textMargin = latinTextMargin().get(),
            paddingMultiplier = latinPaddingMultiplier().get() / 100f,
            ovalHeightMargin = latinOvalHeightMargin().get() / 100f,
            horizontalPadding = latinHorizontalPadding().get(),
            verticalPadding = latinVerticalPadding().get(),
            minFontSize = 10,      // Latin can reduce font
            maxFontSize = 12,      // Latin max font
            lineHeightMultiplier = 1.2f,
            minExpansion = 1.0f,
            maxExpansion = 1.3f,
            useHybridOverflow = true,
        )
    }

    /**
     * Get a reactive Flow of CJK profile that emits when any CJK preference changes.
     */
    fun cjkProfileChanges(): Flow<TranslationProfile> {
        return combine(
            cjkTextMargin().changes(),
            cjkPaddingMultiplier().changes(),
            cjkOvalHeightMargin().changes(),
        ) { textMargin, paddingMult, ovalMargin ->
            TranslationProfile(
                type = ProfileType.CJK,
                textMargin = textMargin,
                paddingMultiplier = paddingMult / 100f,
                ovalHeightMargin = ovalMargin / 100f,
                horizontalPadding = 8,
                verticalPadding = 2,
                minFontSize = 12,
                maxFontSize = 12,
                lineHeightMultiplier = 1.4f,
                minExpansion = 1.8f,
                maxExpansion = 2.5f,
                useHybridOverflow = false,
            )
        }
    }

    /**
     * Get a reactive Flow of Latin profile that emits when any Latin preference changes.
     */
    fun latinProfileChanges(): Flow<TranslationProfile> {
        return combine(
            latinTextMargin().changes(),
            latinPaddingMultiplier().changes(),
            latinOvalHeightMargin().changes(),
            latinHorizontalPadding().changes(),
            latinVerticalPadding().changes(),
        ) { textMargin, paddingMult, ovalMargin, hPadding, vPadding ->
            TranslationProfile(
                type = ProfileType.LATIN,
                textMargin = textMargin,
                paddingMultiplier = paddingMult / 100f,
                ovalHeightMargin = ovalMargin / 100f,
                horizontalPadding = hPadding,
                verticalPadding = vPadding,
                minFontSize = 10,
                maxFontSize = 12,
                lineHeightMultiplier = 1.2f,
                minExpansion = 1.0f,
                maxExpansion = 1.3f,
                useHybridOverflow = true,
            )
        }
    }

    /**
     * Get the appropriate profile for the given source language.
     */
    fun getProfileForLanguage(sourceLanguage: String): TranslationProfile {
        return when (ProfileType.fromSourceLanguage(sourceLanguage)) {
            ProfileType.CJK -> getCJKProfile()
            ProfileType.LATIN -> getLatinProfile()
        }
    }

    /**
     * Get a reactive Flow of the appropriate profile for the given source language.
     */
    fun profileChangesForLanguage(sourceLanguage: String): Flow<TranslationProfile> {
        return when (ProfileType.fromSourceLanguage(sourceLanguage)) {
            ProfileType.CJK -> cjkProfileChanges()
            ProfileType.LATIN -> latinProfileChanges()
        }
    }

    // ============================================================================
    // Reset to Defaults
    // ============================================================================

    companion object {
        // CJK Default Values
        const val DEFAULT_CJK_TEXT_MARGIN = 2
        const val DEFAULT_CJK_PADDING_MULTIPLIER = 115
        const val DEFAULT_CJK_OVAL_HEIGHT_MARGIN = 70

        // Latin Default Values
        const val DEFAULT_LATIN_TEXT_MARGIN = 2
        const val DEFAULT_LATIN_PADDING_MULTIPLIER = 100
        const val DEFAULT_LATIN_OVAL_HEIGHT_MARGIN = 95
        const val DEFAULT_LATIN_HORIZONTAL_PADDING = 6
        const val DEFAULT_LATIN_VERTICAL_PADDING = 4
    }

    /**
     * Reset all CJK display settings to their default values.
     */
    fun resetCJKDefaults() {
        cjkTextMargin().set(DEFAULT_CJK_TEXT_MARGIN)
        cjkPaddingMultiplier().set(DEFAULT_CJK_PADDING_MULTIPLIER)
        cjkOvalHeightMargin().set(DEFAULT_CJK_OVAL_HEIGHT_MARGIN)
    }

    /**
     * Reset all Latin display settings to their default values.
     */
    fun resetLatinDefaults() {
        latinTextMargin().set(DEFAULT_LATIN_TEXT_MARGIN)
        latinPaddingMultiplier().set(DEFAULT_LATIN_PADDING_MULTIPLIER)
        latinOvalHeightMargin().set(DEFAULT_LATIN_OVAL_HEIGHT_MARGIN)
        latinHorizontalPadding().set(DEFAULT_LATIN_HORIZONTAL_PADDING)
        latinVerticalPadding().set(DEFAULT_LATIN_VERTICAL_PADDING)
    }
}
