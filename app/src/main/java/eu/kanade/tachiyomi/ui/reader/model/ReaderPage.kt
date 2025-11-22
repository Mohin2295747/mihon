package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.translation.model.PageTranslation
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var translation: PageTranslation? = null,
    var stream: (() -> InputStream)? = null,
    var fileName: String? = null, // TachiyomiAT: Used for translation deletion
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter
}
