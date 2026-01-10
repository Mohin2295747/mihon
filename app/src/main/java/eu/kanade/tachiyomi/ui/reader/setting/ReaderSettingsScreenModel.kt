package eu.kanade.tachiyomi.ui.reader.setting

import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.domain.translation.model.ProfileType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsScreenModel(
    private val readerState: StateFlow<ReaderViewModel.State>,
    val hasDisplayCutout: Boolean,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences = Injekt.get(),
    val translationPreferences: TranslationPreferences = Injekt.get(),
) : ScreenModel {

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    /**
     * Flow that emits true if the current chapter has translations.
     * Used to conditionally show the Translation settings tab.
     */
    val hasTranslationsFlow: StateFlow<Boolean> = readerState
        .map { state ->
            state.viewerChapters?.currChapter?.pages?.any { page ->
                (page as? ReaderPage)?.translation?.blocks?.isNotEmpty() == true
            } ?: false
        }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, false)

    /**
     * Flow that emits the current profile type based on the source language
     * of translations in the current chapter.
     */
    val currentProfileTypeFlow: StateFlow<ProfileType> = readerState
        .map { state ->
            val sourceLang = state.viewerChapters?.currChapter?.pages
                ?.firstNotNullOfOrNull { page ->
                    (page as? ReaderPage)?.translation?.sourceLanguage
                }
            ProfileType.fromSourceLanguage(sourceLang ?: "auto")
        }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, ProfileType.LATIN)
}
