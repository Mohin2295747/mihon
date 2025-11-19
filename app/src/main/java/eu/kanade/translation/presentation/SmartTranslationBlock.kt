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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.BubbleShape
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max

// Smart bubble sizing constants
private const val MIN_FONT_SIZE = 12f
private const val DEFAULT_FONT_SIZE = 16f
private const val CJK_MAX_FONT_SIZE = 12f // Cap for Korean/Japanese/Chinese translations to prevent oversized fonts
private const val BUBBLE_EXPANSION_RATIO = 1.2f
private const val MAX_BUBBLE_EXPANSION = 1.5f

// Edge padding to prevent text touching bubble boundaries
// Different padding for different bubble shapes
private const val HORIZONTAL_PADDING_DP = 8 // Left/right inset for rectangles
private const val VERTICAL_PADDING_DP = 2 // Top/bottom inset for rectangles (reduced for better vertical space)
private const val OVAL_VERTICAL_PADDING_DP = 10 // Increased vertical padding for horizontal ovals to avoid curved edges
private const val OVAL_HORIZONTAL_PADDING_DP = 4 // Reduced horizontal padding for vertical ovals

// HARD WIDTH CONSTRAINT: 90% rule to prevent horizontal overflow
// If bubble width is 200px, text uses max 180px per line (90%)
private const val HARD_WIDTH_CONSTRAINT = 0.90f // 90% of bubble width - STRICT to prevent overflow

// Safety margins for text area calculation after applying hard constraint
// Rectangles: Use most of available space
private const val WIDTH_SAFETY_MARGIN = 0.98f // Use 98% of constrained width (was 95%, increased since we have hard constraint)
private const val HEIGHT_SAFETY_MARGIN = 0.99f // Use 99% of available height

// Horizontal ovals (Korean common case): Reduce vertical area to avoid curved top/bottom edges
private const val OVAL_WIDTH_SAFETY_MARGIN = 0.98f // Keep 98% width (was 95%, increased with hard constraint)
private const val OVAL_HEIGHT_SAFETY_MARGIN = 0.70f // Use only 70% of height (avoid top/bottom curves) - CRITICAL for Korean ovals

// Vertical ovals: Reduce horizontal area to avoid curved left/right edges
private const val VERTICAL_OVAL_WIDTH_SAFETY_MARGIN = 0.70f // Use only 70% width (avoid left/right curves)
private const val VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN = 0.95f // Keep 95% height

// Korean -> English specific constants
private const val KOREAN_TO_ENGLISH_MIN_EXPANSION = 1.8f
private const val KOREAN_TO_ENGLISH_MAX_EXPANSION = 2.5f
private const val KOREAN_LINE_HEIGHT_MULTIPLIER = 1.4f // Reduced from 1.7f to pack lines closer and utilize vertical space better
private const val GENERIC_LINE_HEIGHT_MULTIPLIER = 1.3f // Reduced from 1.5f for better vertical space utilization

// Cloud Translation specific constants (longer translations)
private const val CLOUD_KOREAN_TO_ENGLISH_MIN_EXPANSION = 2.2f
private const val CLOUD_KOREAN_TO_ENGLISH_MAX_EXPANSION = 3.0f
private const val CLOUD_TRANSLATION_EXPANSION_MULTIPLIER = 1.15f

/**
 * Helper data class for shape-specific padding and margin configurations
 */
private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,
    sourceLanguage: String = "auto", // Source language detected by OCR
    targetLanguage: String = "en", // Target language for translation
    translatorType: String = "gemini", // Translator engine type (cloud, gemini, etc.)
) {
    // Language-specific padding multipliers to prevent text overlap
    val paddingMultiplier = when (sourceLanguage) {
        "ko", "korean" -> 1.15f // Reduced from 1.5f to prevent oversized bubbles and fonts
        "ja", "japanese" -> 1.2f // Japanese with Kanji also benefits from extra padding
        else -> 1.0f
    }

    val padX = (block.symWidth * 2) * paddingMultiplier
    val padY = block.symHeight * paddingMultiplier
    val xPx = max((block.x - padX / 2) * scaleFactor, 0.0f)
    val yPx = max((block.y - padY / 2) * scaleFactor, 0.0f)

    // Calculate raw bubble dimensions (100% size for background)
    val rawWidth = ((block.width + padX) * scaleFactor).pxToDp()
    val rawHeight = ((block.height + padY) * scaleFactor).pxToDp()
    val isVertical = block.angle > 85

    // Detect Korean -> English translation pair
    val isKoreanToEnglish = isKoreanToEnglishTranslation(sourceLanguage, targetLanguage)

    // Calculate optimal dimensions with expansion (if needed for Korean->English)
    // Then apply HARD 90% WIDTH CONSTRAINT to final result
    val calculatedDimensions = remember(block.translation, isKoreanToEnglish, translatorType) {
        val expandedDimensions = calculateOptimalDimensions(
            text = block.translation,
            originalText = block.text,
            baseWidth = rawWidth,  // Start with full width
            baseHeight = rawHeight,
            fontFamily = fontFamily,
            isKoreanToEnglish = isKoreanToEnglish,
            translatorType = translatorType
        )

        // APPLY HARD 90% WIDTH CONSTRAINT TO FINAL EXPANDED DIMENSIONS
        // If bubble width = 200px:
        // - Background uses 200px (100%)
        // - Text limited to 180px (90%) max, even after expansion
        // This prevents text from overflowing beyond white background
        BubbleDimensions(
            width = minOf(expandedDimensions.width, rawWidth * HARD_WIDTH_CONSTRAINT),
            height = expandedDimensions.height,  // No height constraint
            expansionRatio = expandedDimensions.expansionRatio
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
            // Detect bubble shape for adaptive text constraints
            val bubbleShape = block.detectShape()

            // Apply shape-specific padding and safety margins
            // Horizontal ovals (Korean common): More vertical padding, reduce vertical area to avoid curves
            // Vertical ovals: More horizontal padding, reduce horizontal area
            // Rectangles/Squares: Standard padding, maximize space usage
            val (horizontalPaddingDp, verticalPaddingDp, widthMargin, heightMargin) = when (bubbleShape) {
                BubbleShape.HORIZONTAL_OVAL -> {
                    // Korean horizontal oval bubbles - CRITICAL CASE
                    // Text gets cut off at curved top/bottom edges
                    // Solution: Use only middle 70% of height, increase vertical padding
                    Quadruple(
                        HORIZONTAL_PADDING_DP,        // 8dp horizontal
                        OVAL_VERTICAL_PADDING_DP,     // 10dp vertical (increased to avoid curves)
                        OVAL_WIDTH_SAFETY_MARGIN,     // 95% width (keep full width)
                        OVAL_HEIGHT_SAFETY_MARGIN     // 70% height (avoid curves!) - KEY FIX
                    )
                }
                BubbleShape.VERTICAL_OVAL -> {
                    // Vertical oval bubbles - less common
                    // Text gets cut off at curved left/right edges
                    Quadruple(
                        OVAL_HORIZONTAL_PADDING_DP,          // 4dp horizontal
                        VERTICAL_PADDING_DP,                 // 2dp vertical
                        VERTICAL_OVAL_WIDTH_SAFETY_MARGIN,   // 70% width (avoid curves)
                        VERTICAL_OVAL_HEIGHT_SAFETY_MARGIN   // 95% height
                    )
                }
                else -> {
                    // Rectangles and squares - standard case
                    // Maximize vertical space usage for better text fit
                    Quadruple(
                        HORIZONTAL_PADDING_DP,  // 8dp horizontal
                        VERTICAL_PADDING_DP,    // 2dp vertical (reduced for max space)
                        WIDTH_SAFETY_MARGIN,    // 95% width
                        HEIGHT_SAFETY_MARGIN    // 99% height (maximized for rectangles)
                    )
                }
            }

            // Apply edge padding to prevent text touching bubble edges
            val horizontalPaddingPx = with(density) { (horizontalPaddingDp.dp).roundToPx() }
            val verticalPaddingPx = with(density) { (verticalPaddingDp.dp).roundToPx() }

            // Calculate available text area with shape-aware padding and safety margins
            val totalHorizontalPadding = horizontalPaddingPx * 2
            val totalVerticalPadding = verticalPaddingPx * 2

            val maxWidthPx = with(density) {
                ((calculatedDimensions.width.roundToPx() - totalHorizontalPadding) * widthMargin).toInt()
            }
            val maxHeightPx = with(density) {
                ((calculatedDimensions.height.roundToPx() - totalVerticalPadding) * heightMargin).toInt()
                    .coerceAtLeast(0)  // Prevent negative values
            }

            // Calculate actual constrained width for text layout (with padding applied)
            val constrainedWidthDp = with(density) { maxWidthPx.toDp() }

            // Binary search for optimal font size with minimum threshold
            // Cap maximum font size to 12dp for all languages for consistency
            val isCJKLanguage = sourceLanguage.lowercase() in listOf("ko", "korean", "ja", "japanese", "zh", "chinese")
            var low = MIN_FONT_SIZE.toInt()
            var high = CJK_MAX_FONT_SIZE.toInt()  // Use 12dp for all languages
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
                        textAlign = TextAlign.Justify,
                        maxLines = calculateMaxLines(maxHeightPx, mid, isKoreanToEnglish),
                        softWrap = true,
                        modifier = Modifier
                            .width(constrainedWidthDp)
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

            // Measure final layout with constrained width to prevent horizontal clipping
            val textPlaceable = subcompose(Unit) {
                Text(
                    text = block.translation,
                    fontSize = finalFontSize,
                    fontFamily = fontFamily,
                    color = Color.Black,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Justify,
                    maxLines = calculateMaxLines(maxHeightPx, bestSize, isKoreanToEnglish),
                    modifier = Modifier
                        .width(constrainedWidthDp)
                        .rotate(if (isVertical) 0f else block.angle)
                        .align(Alignment.Center),
                )
            }[0].measure(Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx))

            layout(textPlaceable.width, textPlaceable.height) {
                textPlaceable.place(0, 0)
            }
        }
    }
}

/**
 * Calculate optimal bubble dimensions with expansion if needed
 * Includes special handling for Korean -> English translation
 * Applies translator-specific optimizations (e.g., Cloud Translation produces longer text)
 */
private fun calculateOptimalDimensions(
    text: String,
    originalText: String,
    baseWidth: Dp,
    baseHeight: Dp,
    fontFamily: FontFamily,
    isKoreanToEnglish: Boolean,
    translatorType: String = "gemini",
): BubbleDimensions {
    // For Korean -> English, apply specialized expansion algorithm
    if (isKoreanToEnglish) {
        return calculateKoreanToEnglishDimensions(
            text,
            originalText,
            baseWidth,
            baseHeight,
            translatorType
        )
    }

    // For short text, use base dimensions
    if (text.length < 20) {
        return BubbleDimensions(baseWidth, baseHeight, 1.0f)
    }

    // For longer text that might not fit at minimum font size,
    // apply expansion proportionally
    // Cloud Translation produces longer output, so apply additional expansion
    val isCloudTranslation = translatorType.lowercase() == "cloud"
    val cloudMultiplier = if (isCloudTranslation) CLOUD_TRANSLATION_EXPANSION_MULTIPLIER else 1.0f

    val estimatedExpansionNeeded = when {
        text.length > 200 -> MAX_BUBBLE_EXPANSION * cloudMultiplier
        text.length > 100 -> BUBBLE_EXPANSION_RATIO * cloudMultiplier
        else -> 1.0f
    }

    val maxExpansion = if (isCloudTranslation) {
        (MAX_BUBBLE_EXPANSION * CLOUD_TRANSLATION_EXPANSION_MULTIPLIER).coerceAtMost(2.0f)
    } else {
        MAX_BUBBLE_EXPANSION
    }

    val expansionRatio = estimatedExpansionNeeded.coerceAtMost(maxExpansion)

    return BubbleDimensions(
        width = baseWidth * expansionRatio,
        height = baseHeight * expansionRatio,
        expansionRatio = expansionRatio
    )
}

/**
 * Calculate bubble dimensions specifically for Korean -> English translation
 * Korean text is extremely compact; English translations typically expand 1.8-2.5x
 * Cloud Translation produces longer output (2.2-3.0x) due to more literal translation
 */
private fun calculateKoreanToEnglishDimensions(
    englishText: String,
    koreanText: String,
    baseWidth: Dp,
    baseHeight: Dp,
    translatorType: String = "gemini",
): BubbleDimensions {
    // Cloud Translation produces longer, more literal translations
    val isCloudTranslation = translatorType.lowercase() == "cloud"
    val minExpansion = if (isCloudTranslation) {
        CLOUD_KOREAN_TO_ENGLISH_MIN_EXPANSION
    } else {
        KOREAN_TO_ENGLISH_MIN_EXPANSION
    }
    val maxExpansion = if (isCloudTranslation) {
        CLOUD_KOREAN_TO_ENGLISH_MAX_EXPANSION
    } else {
        KOREAN_TO_ENGLISH_MAX_EXPANSION
    }

    // Calculate length ratio (English characters / Korean characters)
    val lengthRatio = if (koreanText.isNotEmpty()) {
        englishText.length.toFloat() / koreanText.length.toFloat()
    } else {
        2.0f // Default assumption
    }

    // Determine expansion ratio based on length ratio and absolute lengths
    val expansionRatio = when {
        // Very long translations need maximum expansion
        englishText.length > 200 -> maxExpansion

        // High length ratio (English much longer than Korean)
        lengthRatio > 3.0f -> maxExpansion
        lengthRatio > 2.0f -> maxExpansion * 0.9f

        // Medium length text
        englishText.length > 100 -> minExpansion * 1.2f
        englishText.length > 50 -> minExpansion

        // Short text (less aggressive expansion)
        englishText.length < 20 -> 1.3f

        // Default case
        else -> minExpansion
    }.coerceAtMost(maxExpansion)

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
 * Uses optimized line height multipliers to maximize vertical space utilization
 * while maintaining readability
 */
private fun calculateMaxLines(heightPx: Int, fontSize: Int, isKoreanToEnglish: Boolean): Int {
    // Use reduced line height multipliers to pack lines closer together
    // This allows better utilization of vertical space and reduces wasted space at bottom of bubbles
    val lineHeightMultiplier = if (isKoreanToEnglish) {
        KOREAN_LINE_HEIGHT_MULTIPLIER // 1.4x for Korean→English
    } else {
        GENERIC_LINE_HEIGHT_MULTIPLIER // 1.3x for other languages
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
