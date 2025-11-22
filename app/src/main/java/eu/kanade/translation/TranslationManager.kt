package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter as DbChapter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationManager(
    private val context: Context,
    private val provider: TranslationProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) {
    private val translator = ChapterTranslator(context, provider)

    val isRunning: Boolean
        get() = translator.isRunning

    val queueState
        get() = translator.queueState

    fun translatorStart() = translator.start()
    fun translatorStop(reason: String? = null) = translator.stop(reason)

    fun startTranslation() {
        if (translator.isRunning) return
        translator.start()
    }

    fun pauseTranslation() {
        translator.pause()
        translator.stop()
    }

    fun clearQueue() {
        translator.clearQueue()
        translator.stop()
    }

    fun getQueuedTranslationOrNull(chapterId: Long): Translation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun translateChapter(manga: Manga, chapters: Chapter) {
        translator.queueChapter(manga, chapters)
        startTranslation()
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        sourceId: Long,
    ): Translation.State {
        val translation = getQueuedTranslationOrNull(chapterId)
        if (translation != null) return translation.status
        if (isChapterTranslated(chapterName, scanlator, title, sourceId)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun isChapterTranslated(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        val source = sourceManager.get(sourceId)
        if (source == null) return false
        val file = provider.findTranslationFile(chapterName, chapterScanlator, mangaTitle, source)
        return file?.exists() == true
    }
    fun getChapterTranslation(
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Map<String, PageTranslation> {
        try {
            val file = provider.findTranslationFile(
                chapterName,
                scanlator,
                title,
                source,
            ) ?: return emptyMap()
            return getChapterTranslation(file)
        } catch (_: Exception) {
        }
        return emptyMap()
    }

    fun getChapterTranslation(
        file: UniFile,
    ): Map<String, PageTranslation> {
        try {
            return Json.decodeFromStream<Map<String, PageTranslation>>(file.openInputStream())
        } catch (e: Exception) {
            file.delete()
        }
        return emptyMap()
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        launchIO {
            removeFromTranslationQueue(chapter)
            val file = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
            file?.delete()
        }
    }

    /**
     * Delete a single translation block from a specific page
     * @param chapter The chapter containing the translation
     * @param manga The manga containing the chapter
     * @param source The source of the manga
     * @param pageFileName The filename of the page (e.g., "page_5")
     * @param blockIndex The index of the block to delete in the page's blocks list
     * @return true if deletion successful, false otherwise
     */
    fun deleteTranslationBlock(
        chapter: Chapter,
        manga: Manga,
        source: Source,
        pageFileName: String,
        blockIndex: Int,
    ): Boolean {
        try {
            val file = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
            if (file == null) {
                logcat(LogPriority.ERROR) { "deleteTranslationBlock: Translation file not found for chapter '${chapter.name}'" }
                return false
            }

            // Load current translations
            val translations = Json.decodeFromStream<MutableMap<String, PageTranslation>>(
                file.openInputStream(),
            ).toMutableMap()

            // Remove the specific block from the page
            val pageTranslation = translations[pageFileName]
            if (pageTranslation == null) {
                logcat(LogPriority.ERROR) { "deleteTranslationBlock: Page '$pageFileName' not found in translation file" }
                return false
            }

            if (blockIndex < 0 || blockIndex >= pageTranslation.blocks.size) {
                logcat(LogPriority.ERROR) {
                    "deleteTranslationBlock: Invalid blockIndex $blockIndex (valid range: 0-${pageTranslation.blocks.size - 1})"
                }
                return false
            }

            logcat { "deleteTranslationBlock: Removing block $blockIndex from page '$pageFileName'" }
            pageTranslation.blocks.removeAt(blockIndex)

            // If no blocks left on this page, remove the page entry entirely
            if (pageTranslation.blocks.isEmpty()) {
                logcat { "deleteTranslationBlock: No blocks left on page '$pageFileName', removing page entry" }
                translations.remove(pageFileName)
            }

            // If no pages left in the translation, delete the entire file
            if (translations.isEmpty()) {
                logcat { "deleteTranslationBlock: No pages left in translation file, deleting file" }
                file.delete()
                return true
            }

            // Save updated translation JSON
            file.openOutputStream().use { stream ->
                Json.encodeToStream(translations, stream)
            }

            logcat { "deleteTranslationBlock: Successfully persisted deletion to file" }
            return true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "deleteTranslationBlock: Exception occurred: ${e.message}\n${e.stackTraceToString()}"
            }
            return false
        }
    }

    /**
     * Overload for deleting translation block with database Chapter model
     */
    fun deleteTranslationBlock(
        chapter: DbChapter,
        manga: Manga,
        source: Source,
        pageFileName: String,
        blockIndex: Int,
    ): Boolean {
        try {
            val file = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
            if (file == null) {
                logcat(LogPriority.ERROR) { "deleteTranslationBlock: Translation file not found for chapter '${chapter.name}'" }
                return false
            }

            // Load current translations
            val translations = Json.decodeFromStream<MutableMap<String, PageTranslation>>(
                file.openInputStream(),
            ).toMutableMap()

            // Remove the specific block from the page
            val pageTranslation = translations[pageFileName]
            if (pageTranslation == null) {
                logcat(LogPriority.ERROR) { "deleteTranslationBlock: Page '$pageFileName' not found in translation file" }
                return false
            }

            if (blockIndex < 0 || blockIndex >= pageTranslation.blocks.size) {
                logcat(LogPriority.ERROR) {
                    "deleteTranslationBlock: Invalid blockIndex $blockIndex (valid range: 0-${pageTranslation.blocks.size - 1})"
                }
                return false
            }

            logcat { "deleteTranslationBlock: Removing block $blockIndex from page '$pageFileName'" }
            pageTranslation.blocks.removeAt(blockIndex)

            // If no blocks left on this page, remove the page entry entirely
            if (pageTranslation.blocks.isEmpty()) {
                logcat { "deleteTranslationBlock: No blocks left on page '$pageFileName', removing page entry" }
                translations.remove(pageFileName)
            }

            // If no pages left in the translation, delete the entire file
            if (translations.isEmpty()) {
                logcat { "deleteTranslationBlock: No pages left in translation file, deleting file" }
                file.delete()
                return true
            }

            // Save updated translation JSON
            file.openOutputStream().use { stream ->
                Json.encodeToStream(translations, stream)
            }

            logcat { "deleteTranslationBlock: Successfully persisted deletion to file" }
            return true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "deleteTranslationBlock: Exception occurred: ${e.message}\n${e.stackTraceToString()}"
            }
            return false
        }
    }

    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                translator.removeFromQueue(manga)
            }
            provider.findMangaDir(manga.title, source)?.delete()
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
            }
        }
    }

    fun cancelQueuedTranslation(translation: Translation) {
        removeFromTranslationQueue(translation.chapter)
    }

    private fun removeFromTranslationQueue(chapter: Chapter) {
        val wasRunning = translator.isRunning
        if (wasRunning) {
            translator.pause()
        }
        translator.removeFromQueue(chapter)
        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                translator.stop()
            } else if (queueState.value.isNotEmpty()) {
                translator.start()
            }
        }
    }

    fun statusFlow(): Flow<Translation> = queueState
        .flatMapLatest { translations ->
            translations
                .map { translation ->
                    translation.statusFlow.drop(1).map { translation }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { translation -> translation.status == Translation.State.TRANSLATING }.asFlow(),
            )
        }
}
