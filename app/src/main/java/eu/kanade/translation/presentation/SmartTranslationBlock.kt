package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max

// Smart bubble sizing constants
private const val MIN_FONT_SIZE = 12f
private const val DEFAULT_FONT_SIZE = 16f
private const val BUBBLE_EXPANSION_RATIO = 1.2f
private const val MAX_BUBBLE_EXPANSION = 1.5f

// Korean -> English specific constants
private const val KOREAN_TO_ENGLISH_MIN_EXPANSION = 1.8f
private const val KOREAN_TO_ENGLISH_MAX_EXPANSION = 2.5f
private const val KOREAN_LINE_HEIGHT_MULTIPLIER = 1.7f
private const val GENERIC_LINE_HEIGHT_MULTIPLIER = 1.5f

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,
    sourceLanguage: String = "auto", // Source language detected by OCR
    targetLanguage: String = "en", // Target language for translation
) {
    // Language-specific padding multipliers to prevent text overlap
    val paddingMultiplier = when (sourceLanguage) {
        "ko", "korean" -> 1.5f // Korean needs more padding due to complex Hangul shapes
        "ja", "japanese" -> 1.2f // Japanese with Kanji also benefits from extra padding
        else -> 1.0f
    }

    val padX = (block.symWidth * 2) * paddingMultiplier
    val padY = block.symHeight * paddingMultiplier
    val xPx = max((block.x - padX / 2) * scaleFactor, 0.0f)
    val yPx = max((block.y - padY / 2) * scaleFactor, 0.0f)
    val baseWidth = ((block.width + padX) * scaleFactor).pxToDp()
    val baseHeight = ((block.height + padY) * scaleFactor).pxToDp()
    val isVertical = block.angle > 85

    // Detect Korean -> English translation pair
    val isKoreanToEnglish = isKoreanToEnglishTranslation(sourceLanguage, targetLanguage)

    // Remember calculated dimensions to avoid recalculation
    val calculatedDimensions = remember(block.translation, isKoreanToEnglish) {
        calculateOptimalDimensions(
            text = block.translation,
            originalText = block.text,
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            fontFamily = fontFamily,
            isKoreanToEnglish = isKoreanToEnglish
        )
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.CenterStart, true)
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(calculatedDimensions.width, calculatedDimensions.height),
    ) {
        val density = LocalDensity.current
        SubcomposeLayout { constraints ->
            val maxWidthPx = with(density) { calculatedDimensions.width.roundToPx() }
            val maxHeightPx = with(density) { calculatedDimensions.height.roundToPx() }

            // Binary search for optimal font size with minimum threshold
            var low = MIN_FONT_SIZE.toInt()
            var high = 100 // Initial upper bound
            var bestSize = low

            while (low <= high) {
                val mid = ((low + high) / 2).coerceAtLeast(MIN_FONT_SIZE.toInt())
                val textLayoutResult = subcompose(mid.sp) {
                    Text(
                        text = block.translation,
                        fontSize = mid.sp,
                        fontFamily = fontFamily,
                        color = Color.Black,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        maxLines = calculateMaxLines(maxHeightPx, mid, isKoreanToEnglish),
                        softWrap = true,
                        modifier = Modifier
                            .width(calculatedDimensions.width)
                            .rotate(if (isVertical) 0f else block.angle)
                            .align(Alignment.Center),
                    )
                }[0].measure(Constraints(maxWidth = maxWidthPx))

                if (textLayoutResult.height <= maxHeightPx && textLayoutResult.width <= maxWidthPx) {
                    bestSize = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            val finalFontSize = bestSize.coerceAtLeast(MIN_FONT_SIZE.toInt()).sp

            // Measure final layout
            val textPlaceable = subcompose(Unit) {
                Text(
                    text = block.translation,
                    fontSize = finalFontSize,
                    fontFamily = fontFamily,
                    color = Color.Black,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    maxLines = calculateMaxLines(maxHeightPx, bestSize, isKoreanToEnglish),
                    modifier = Modifier
                        .width(calculatedDimensions.width)
                        .rotate(if (isVertical) 0f else block.angle)
                        .align(Alignment.Center),
                )
            }[0].measure(constraints)

            layout(textPlaceable.width, textPlaceable.height) {
                textPlaceable.place(0, 0)
            }
        }
    }
}

/**
 * Calculate optimal bubble dimensions with expansion if needed
 * Includes special handling for Korean -> English translation
 */
private fun calculateOptimalDimensions(
    text: String,
    originalText: String,
    baseWidth: Dp,
    baseHeight: Dp,
    fontFamily: FontFamily,
    isKoreanToEnglish: Boolean,
): BubbleDimensions {
    // For Korean -> English, apply specialized expansion algorithm
    if (isKoreanToEnglish) {
        return calculateKoreanToEnglishDimensions(text, originalText, baseWidth, baseHeight)
    }

    // For short text, use base dimensions
    if (text.length < 20) {
        return BubbleDimensions(baseWidth, baseHeight, 1.0f)
    }

    // For longer text that might not fit at minimum font size,
    // apply expansion proportionally
    val estimatedExpansionNeeded = when {
        text.length > 200 -> MAX_BUBBLE_EXPANSION
        text.length > 100 -> BUBBLE_EXPANSION_RATIO
        else -> 1.0f
    }

    val expansionRatio = estimatedExpansionNeeded.coerceAtMost(MAX_BUBBLE_EXPANSION)

    return BubbleDimensions(
        width = baseWidth * expansionRatio,
        height = baseHeight * expansionRatio,
        expansionRatio = expansionRatio
    )
}

/**
 * Calculate bubble dimensions specifically for Korean -> English translation
 * Korean text is extremely compact; English translations typically expand 1.8-2.5x
 */
private fun calculateKoreanToEnglishDimensions(
    englishText: String,
    koreanText: String,
    baseWidth: Dp,
    baseHeight: Dp,
): BubbleDimensions {
    // Calculate length ratio (English characters / Korean characters)
    val lengthRatio = if (koreanText.isNotEmpty()) {
        englishText.length.toFloat() / koreanText.length.toFloat()
    } else {
        2.0f // Default assumption
    }

    // Determine expansion ratio based on length ratio and absolute lengths
    val expansionRatio = when {
        // Very long translations need maximum expansion
        englishText.length > 200 -> KOREAN_TO_ENGLISH_MAX_EXPANSION

        // High length ratio (English much longer than Korean)
        lengthRatio > 3.0f -> KOREAN_TO_ENGLISH_MAX_EXPANSION
        lengthRatio > 2.0f -> KOREAN_TO_ENGLISH_MAX_EXPANSION * 0.9f

        // Medium length text
        englishText.length > 100 -> KOREAN_TO_ENGLISH_MIN_EXPANSION * 1.2f
        englishText.length > 50 -> KOREAN_TO_ENGLISH_MIN_EXPANSION

        // Short text (less aggressive expansion)
        englishText.length < 20 -> 1.3f

        // Default case
        else -> KOREAN_TO_ENGLISH_MIN_EXPANSION
    }.coerceAtMost(KOREAN_TO_ENGLISH_MAX_EXPANSION)

    // Apply minimum width constraint for Korean->English to prevent narrow tall bubbles
    val minWidthForKorean = baseWidth * 1.4f
    val expandedWidth = (baseWidth * expansionRatio).coerceAtLeast(minWidthForKorean)

    return BubbleDimensions(
        width = expandedWidth,
        height = baseHeight * expansionRatio,
        expansionRatio = expansionRatio
    )
}

/**
 * Calculate maximum lines based on bubble height and font size
 * Uses Korean-aware line height multiplier for better Hangul rendering
 */
private fun calculateMaxLines(heightPx: Int, fontSize: Int, isKoreanToEnglish: Boolean): Int {
    // Korean Hangul requires slightly more line height due to vertical stacking of consonants/vowels
    val lineHeightMultiplier = if (isKoreanToEnglish) {
        KOREAN_LINE_HEIGHT_MULTIPLIER
    } else {
        GENERIC_LINE_HEIGHT_MULTIPLIER
    }

    val approximateLineHeight = fontSize * lineHeightMultiplier
    val maxLines = (heightPx / approximateLineHeight).toInt().coerceAtLeast(1)
    // Cap at reasonable maximum to prevent excessive lines
    return maxLines.coerceAtMost(20)
}

/**
 * Data class to hold calculated bubble dimensions
 */
private data class BubbleDimensions(
    val width: Dp,
    val height: Dp,
    val expansionRatio: Float
)

/**
 * Detect if this is a Korean -> English translation pair
 */
private fun isKoreanToEnglishTranslation(sourceLanguage: String, targetLanguage: String): Boolean {
    val isKoreanSource = sourceLanguage.lowercase() in listOf("ko", "korean", "kor")
    val isEnglishTarget = targetLanguage.lowercase() in listOf("en", "english", "eng")
    return isKoreanSource && isEnglishTarget
}
