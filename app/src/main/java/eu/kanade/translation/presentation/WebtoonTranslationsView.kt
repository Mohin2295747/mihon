package eu.kanade.translation.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.PageTranslation

class WebtoonTranslationsView :
    AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = PageTranslation.EMPTY
        this.font = TranslationFont.ANIME_ACE
        this.fontFamily = Font(
            resId = font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translation: PageTranslation,
        font: TranslationFont? = null,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = translation
        this.font = font ?: TranslationFont.ANIME_ACE
        this.fontFamily = Font(
            resId = this.font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
    }

    @Composable
    override fun Content() {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) {
                        hide()
                    } else {
                        show()
                    }
                },
        ) {
            if (size == IntSize.Zero) return
            val scaleFactor = size.width / translation.imgWidth
            TextBlockBackground(scaleFactor)
            TextBlockContent(scaleFactor)
        }
    }

    @Composable
    fun TextBlockBackground(scaleFactor: Float) {
        // Language-specific padding multipliers to prevent text overlap
        val paddingMultiplier = when (translation.sourceLanguage) {
            "ko", "korean" -> 1.15f // Reduced from 1.5f to match SmartTranslationBlock
            "ja", "japanese" -> 1.2f // Japanese with Kanji also benefits from extra padding
            else -> 1.0f
        }

        // Detect Korean -> English translation for moderate expansion
        val isKoreanToEnglish = (translation.sourceLanguage.lowercase() in listOf("ko", "korean", "kor")) &&
            (translation.targetLanguage.lowercase() in listOf("en", "english", "eng"))

        translation.blocks.forEach { block ->
            val padX = (block.symWidth / 2) * paddingMultiplier
            val padY = (block.symHeight / 2) * paddingMultiplier
            val baseBgX = (block.x - padX / 2) * scaleFactor
            val baseBgY = (block.y - padY / 2) * scaleFactor
            val baseBgWidth = (block.width + padX) * scaleFactor
            val baseBgHeight = (block.height + padY) * scaleFactor

            // Apply moderate expansion for Korean -> English (hybrid approach)
            val expansionRatio = if (isKoreanToEnglish) {
                // Moderate expansion: 1.2x-1.5x based on translation length
                when {
                    block.translation.length > 100 -> 1.5f
                    block.translation.length > 50 -> 1.35f
                    else -> 1.2f
                }
            } else {
                1.0f
            }

            val expandedWidth = baseBgWidth * expansionRatio
            val expandedHeight = baseBgHeight * expansionRatio

            // Center the expanded whitewash over the original bubble position
            val widthExpansion = expandedWidth - baseBgWidth
            val heightExpansion = expandedHeight - baseBgHeight
            val bgX = baseBgX - (widthExpansion / 2)
            val bgY = baseBgY - (heightExpansion / 2)

            val isVertical = block.angle > 85
            Box(
                modifier = Modifier
                    .offset(bgX.pxToDp(), bgY.pxToDp())
                    .size(expandedWidth.pxToDp(), expandedHeight.pxToDp())
                    .rotate(if (isVertical) 0f else block.angle)
                    .background(Color.White, shape = RoundedCornerShape(4.dp)),
            )
        }
    }

    @Composable
    fun TextBlockContent(scaleFactor: Float) {
        translation.blocks.forEach { block ->
            SmartTranslationBlock(
                block = block,
                scaleFactor = scaleFactor,
                fontFamily = fontFamily,
                sourceLanguage = translation.sourceLanguage,
                targetLanguage = translation.targetLanguage,
            )
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}
