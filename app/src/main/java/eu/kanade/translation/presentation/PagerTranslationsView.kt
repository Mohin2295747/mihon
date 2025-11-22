package eu.kanade.translation.presentation

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.BubbleShape
import eu.kanade.translation.model.PageTranslation
import kotlinx.coroutines.flow.MutableStateFlow

class PagerTranslationsView :
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

    val scaleState = MutableStateFlow(1f)
    val viewTLState = MutableStateFlow(PointF())

    // Callback for when a translation block is deleted
    var onBlockDelete: ((Int) -> Unit)? = null

    @Composable
    override fun Content() {
        val viewTL by viewTLState.collectAsState()
        val scale by scaleState.collectAsState()

        // State for popup menu
        var showMenu by remember { mutableStateOf(false) }
        var selectedBlockIndex by remember { mutableStateOf<Int?>(null) }

        Box(
            modifier = Modifier
                .absoluteOffset(viewTL.x.pxToDp(), viewTL.y.pxToDp()),
        ) {
            TextBlockBackground(scale)
            TextBlockContent(scale) { index ->
                selectedBlockIndex = index
                showMenu = true
            }

            // Show popup menu when bubble is clicked
            if (showMenu && selectedBlockIndex != null) {
                TranslationBubbleMenu(
                    expanded = showMenu,
                    onDismiss = {
                        showMenu = false
                        selectedBlockIndex = null
                    },
                    onDelete = {
                        onBlockDelete?.invoke(selectedBlockIndex!!)
                        showMenu = false
                        selectedBlockIndex = null
                    },
                )
            }
        }
    }

    @Composable
    fun TextBlockBackground(zoomScale: Float) {
        // Language-specific padding multipliers to prevent text overlap
        val paddingMultiplier = when (translation.sourceLanguage) {
            "ko", "korean" -> 1.5f // Korean needs more padding due to complex Hangul shapes
            "ja", "japanese" -> 1.2f // Japanese with Kanji also benefits from extra padding
            else -> 1.0f
        }

        translation.blocks.forEach { block ->
            val padX = (block.symWidth / 2) * paddingMultiplier
            val padY = (block.symHeight / 2) * paddingMultiplier
            val bgX = ((block.x - padX / 2) * 1) * zoomScale
            val bgY = ((block.y - padY / 2) * 1) * zoomScale
            val bgWidth = (block.width + padX) * zoomScale
            val bgHeight = (block.height + padY) * zoomScale
            val isVertical = block.angle > 85

            // Detect bubble shape for adaptive background rendering
            // Horizontal ovals (Korean common): Use high corner radius (50%) for elliptical appearance
            // Vertical ovals: Use high corner radius for elliptical appearance
            // Rectangles/Squares: Use moderate corner radius
            val backgroundShape = when (block.detectShape()) {
                BubbleShape.HORIZONTAL_OVAL -> RoundedCornerShape(percent = 50) // Elliptical (Korean case)
                BubbleShape.VERTICAL_OVAL -> RoundedCornerShape(percent = 50)   // Elliptical
                BubbleShape.SQUARE -> RoundedCornerShape(8.dp)                  // More rounded square
                else -> RoundedCornerShape(6.dp)                                // Slightly rounded rectangles
            }

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.CenterStart, true)
                    .offset(bgX.pxToDp(), bgY.pxToDp())
                    .requiredSize(bgWidth.pxToDp(), bgHeight.pxToDp())
                    .rotate(if (isVertical) 0f else block.angle)
                    .background(Color.White, shape = backgroundShape),
            )
        }
    }

    @Composable
    fun TextBlockContent(zoomScale: Float, onBlockClick: (Int) -> Unit = {}) {
        translation.blocks.forEachIndexed { index, block ->
            SmartTranslationBlock(
                block = block,
                scaleFactor = zoomScale,
                fontFamily = fontFamily,
                sourceLanguage = translation.sourceLanguage,
                targetLanguage = translation.targetLanguage,
                translatorType = translation.translatorType,
                onBlockClick = if (onBlockDelete != null) {
                    { onBlockClick(index) }
                } else {
                    null
                },
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
