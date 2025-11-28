package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ATMR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val entries = TranslationFont.entries
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = translationPreferences.autoTranslateAfterDownload(),
                title = stringResource(ATMR.strings.pref_translate_after_downloading),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = translationPreferences.translationFont(),
                title = stringResource(ATMR.strings.pref_reader_font),
                entries = entries.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
            ),
            getTranslationLangGroup(translationPreferences),
            getTranslatioEngineGroup(translationPreferences),
            getCJKDisplayGroup(translationPreferences),
            getLatinDisplayGroup(translationPreferences),
            getTranslatioAdvancedGroup(translationPreferences),
        )
    }

    @Composable
    private fun getTranslationLangGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val fromLangs = TextRecognizerLanguage.entries
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_setup),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = translationPreferences.translateFromLanguage(),
                    title = stringResource(ATMR.strings.pref_translate_from),
                    entries = fromLangs.associate { it.name to it.label }.toImmutableMap(),
                ),
            ),
        )
    }

    @Composable
    private fun getTranslatioEngineGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val engines = TextTranslators.entries
        val detectionMethods = mapOf(
            "mlkit" to "MLKit Detection (Default)",
            "google_cloud" to "Google Cloud Auto-Detection"
        )
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_engine),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = translationPreferences.translationEngine(),
                    title = stringResource(ATMR.strings.pref_translator_engine),
                    entries = engines.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = translationPreferences.translationEngineApiKey(),
                    subtitle = stringResource(ATMR.strings.pref_sub_engine_api_key),
                    title = stringResource(ATMR.strings.pref_engine_api_key),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = translationPreferences.translationLanguageDetectionMethod(),
                    title = "Language Detection Method",
                    subtitle = "For Google Cloud Translation AUTO mode",
                    entries = detectionMethods.toImmutableMap(),
                ),
            ),
        )
    }

    @Composable
    private fun getTranslatioAdvancedGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    pref = translationPreferences.translationEngineModel(),
                    title = stringResource(ATMR.strings.pref_engine_model),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = translationPreferences.translationEngineTemperature(),
                    title = stringResource(ATMR.strings.pref_engine_temperature),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = translationPreferences.translationEngineMaxOutputTokens(),
                    title = stringResource(ATMR.strings.pref_engine_max_output),
                ),
            ),
        )
    }

    @Composable
    private fun getCJKDisplayGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        // CJK Text Margin (0-8px)
        val cjkMarginPref = translationPreferences.cjkTextMargin()
        val cjkMargin by cjkMarginPref.collectAsState()

        // CJK Padding Multiplier (100-150%)
        val cjkPaddingPref = translationPreferences.cjkPaddingMultiplier()
        val cjkPadding by cjkPaddingPref.collectAsState()

        // CJK Oval Height Margin (50-95%)
        val cjkOvalHeightPref = translationPreferences.cjkOvalHeightMargin()
        val cjkOvalHeight by cjkOvalHeightPref.collectAsState()

        return Preference.PreferenceGroup(
            title = "CJK Display (Korean/Japanese/Chinese)",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = cjkMargin,
                    min = 0,
                    max = 8,
                    title = "Text Margin",
                    subtitle = "Spacing from bubble edges: ${cjkMargin}px",
                    onValueChanged = {
                        cjkMarginPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = cjkPadding,
                    min = 100,
                    max = 150,
                    title = "Padding Multiplier",
                    subtitle = "Symbol padding: ${cjkPadding}%",
                    onValueChanged = {
                        cjkPaddingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = cjkOvalHeight,
                    min = 50,
                    max = 95,
                    title = "Oval Height Usage",
                    subtitle = "Text area: ${cjkOvalHeight}% of bubble height",
                    onValueChanged = {
                        cjkOvalHeightPref.set(it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLatinDisplayGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        // Latin Text Margin (0-8px)
        val latinMarginPref = translationPreferences.latinTextMargin()
        val latinMargin by latinMarginPref.collectAsState()

        // Latin Padding Multiplier (100-150%)
        val latinPaddingPref = translationPreferences.latinPaddingMultiplier()
        val latinPadding by latinPaddingPref.collectAsState()

        // Latin Oval Height Margin (70-99%)
        val latinOvalHeightPref = translationPreferences.latinOvalHeightMargin()
        val latinOvalHeight by latinOvalHeightPref.collectAsState()

        // Latin Horizontal Padding (2-12dp)
        val latinHorizontalPaddingPref = translationPreferences.latinHorizontalPadding()
        val latinHorizontalPadding by latinHorizontalPaddingPref.collectAsState()

        // Latin Vertical Padding (2-10dp)
        val latinVerticalPaddingPref = translationPreferences.latinVerticalPadding()
        val latinVerticalPadding by latinVerticalPaddingPref.collectAsState()

        return Preference.PreferenceGroup(
            title = "Latin Display (Spanish/Indonesian)",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = latinMargin,
                    min = 0,
                    max = 8,
                    title = "Text Margin",
                    subtitle = "Spacing from bubble edges: ${latinMargin}px",
                    onValueChanged = {
                        latinMarginPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = latinOvalHeight,
                    min = 70,
                    max = 99,
                    title = "Oval Height Usage",
                    subtitle = "Text area: ${latinOvalHeight}% of bubble height",
                    onValueChanged = {
                        latinOvalHeightPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = latinHorizontalPadding,
                    min = 2,
                    max = 12,
                    title = "Horizontal Padding",
                    subtitle = "Left/right padding: ${latinHorizontalPadding}dp",
                    onValueChanged = {
                        latinHorizontalPaddingPref.set(it)
                        true
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = latinVerticalPadding,
                    min = 2,
                    max = 10,
                    title = "Vertical Padding",
                    subtitle = "Top/bottom padding: ${latinVerticalPadding}dp",
                    onValueChanged = {
                        latinVerticalPaddingPref.set(it)
                        true
                    },
                ),
            ),
        )
    }
}
