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

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,

) {
    val padX = block.symWidth * 2
    val padY = block.symHeight
    val xPx = max((block.x - padX / 2) * scaleFactor, 0.0f)
    val yPx = max((block.y - padY / 2) * scaleFactor, 0.0f)
    val baseWidth = ((block.width + padX) * scaleFactor).pxToDp()
    val baseHeight = ((block.height + padY) * scaleFactor).pxToDp()
    val isVertical = block.angle > 85

    // Remember calculated dimensions to avoid recalculation
    val calculatedDimensions = remember(block.translation) {
        calculateOptimalDimensions(block.translation, baseWidth, baseHeight, fontFamily)
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
                        maxLines = calculateMaxLines(maxHeightPx, mid),
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
                    maxLines = calculateMaxLines(maxHeightPx, bestSize),
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
 */
private fun calculateOptimalDimensions(
    text: String,
    baseWidth: Dp,
    baseHeight: Dp,
    fontFamily: FontFamily,
): BubbleDimensions {
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
 * Calculate maximum lines based on bubble height and font size
 */
private fun calculateMaxLines(heightPx: Int, fontSize: Int): Int {
    // Approximate line height as fontSize * 1.5 (typical line spacing)
    val approximateLineHeight = fontSize * 1.5f
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
