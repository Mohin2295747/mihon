package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.domain.translation.model.ProfileType
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectAsStatePref

/**
 * Translation settings page for the reader settings dialog.
 *
 * Displays BOTH CJK and Latin profile settings that can be adjusted in real-time
 * while viewing a translated chapter. Settings are global and apply to all chapters
 * with the same profile type.
 */
@Composable
internal fun ColumnScope.TranslationPage(screenModel: ReaderSettingsScreenModel) {
    val hasTranslations by screenModel.hasTranslationsFlow.collectAsState()
    val profileType by screenModel.currentProfileTypeFlow.collectAsState()

    if (!hasTranslations) {
        Text(
            text = stringResource(ATMR.strings.pref_translation_no_translations),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    // Show currently active profile indicator
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_active) + ": " +
            when (profileType) {
                ProfileType.CJK -> stringResource(ATMR.strings.pref_translation_profile_cjk)
                ProfileType.LATIN -> stringResource(ATMR.strings.pref_translation_profile_latin)
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    // Show BOTH profiles (CJK and Latin)
    CJKProfileSettings(screenModel)

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    LatinProfileSettings(screenModel)
}

@Composable
private fun ColumnScope.CJKProfileSettings(screenModel: ReaderSettingsScreenModel) {
    val prefs = screenModel.translationPreferences

    // Section header
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_cjk),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val textMarginPref = prefs.cjkTextMargin()
    val textMargin by textMarginPref.collectAsStatePref()

    val paddingMultPref = prefs.cjkPaddingMultiplier()
    val paddingMult by paddingMultPref.collectAsStatePref()

    val ovalHeightPref = prefs.cjkOvalHeightMargin()
    val ovalHeight by ovalHeightPref.collectAsStatePref()

    SliderItem(
        value = textMargin,
        label = stringResource(ATMR.strings.pref_translation_text_margin),
        valueText = "${textMargin}px",
        onChange = { textMarginPref.set(it) },
        min = 0,
        max = 8,
    )

    SliderItem(
        value = paddingMult,
        label = stringResource(ATMR.strings.pref_translation_padding),
        valueText = "${paddingMult}%",
        onChange = { paddingMultPref.set(it) },
        min = 100,
        max = 150,
    )

    SliderItem(
        value = ovalHeight,
        label = stringResource(ATMR.strings.pref_translation_oval_height),
        valueText = "${ovalHeight}%",
        onChange = { ovalHeightPref.set(it) },
        min = 50,
        max = 95,
    )

    // Reset button
    TextButton(
        onClick = { prefs.resetCJKDefaults() },
        modifier = Modifier
            .align(Alignment.End)
            .padding(horizontal = 8.dp),
    ) {
        Text("Reset CJK")
    }
}

@Composable
private fun ColumnScope.LatinProfileSettings(screenModel: ReaderSettingsScreenModel) {
    val prefs = screenModel.translationPreferences

    // Section header
    Text(
        text = stringResource(ATMR.strings.pref_translation_profile_latin),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val textMarginPref = prefs.latinTextMargin()
    val textMargin by textMarginPref.collectAsStatePref()

    val paddingMultPref = prefs.latinPaddingMultiplier()
    val paddingMult by paddingMultPref.collectAsStatePref()

    val ovalHeightPref = prefs.latinOvalHeightMargin()
    val ovalHeight by ovalHeightPref.collectAsStatePref()

    val hPaddingPref = prefs.latinHorizontalPadding()
    val hPadding by hPaddingPref.collectAsStatePref()

    val vPaddingPref = prefs.latinVerticalPadding()
    val vPadding by vPaddingPref.collectAsStatePref()

    SliderItem(
        value = textMargin,
        label = stringResource(ATMR.strings.pref_translation_text_margin),
        valueText = "${textMargin}px",
        onChange = { textMarginPref.set(it) },
        min = 0,
        max = 8,
    )

    SliderItem(
        value = paddingMult,
        label = stringResource(ATMR.strings.pref_translation_padding),
        valueText = "${paddingMult}%",
        onChange = { paddingMultPref.set(it) },
        min = 100,
        max = 150,
    )

    SliderItem(
        value = ovalHeight,
        label = stringResource(ATMR.strings.pref_translation_oval_height),
        valueText = "${ovalHeight}%",
        onChange = { ovalHeightPref.set(it) },
        min = 70,
        max = 99,
    )

    SliderItem(
        value = hPadding,
        label = stringResource(ATMR.strings.pref_translation_h_padding),
        valueText = "${hPadding}dp",
        onChange = { hPaddingPref.set(it) },
        min = 2,
        max = 12,
    )

    SliderItem(
        value = vPadding,
        label = stringResource(ATMR.strings.pref_translation_v_padding),
        valueText = "${vPadding}dp",
        onChange = { vPaddingPref.set(it) },
        min = 2,
        max = 10,
    )

    // Reset button
    TextButton(
        onClick = { prefs.resetLatinDefaults() },
        modifier = Modifier
            .align(Alignment.End)
            .padding(horizontal = 8.dp),
    ) {
        Text("Reset Latin")
    }
}
