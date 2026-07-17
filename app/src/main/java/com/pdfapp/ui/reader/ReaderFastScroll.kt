package com.pdfapp.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drive-style fast scroller: a drag handle hugging the right edge with a
 * transient "page X of N" bubble. Both appear while the list scrolls or the
 * handle is dragged, then fade out after a short delay; dragging the handle
 * flings through the document proportionally.
 */
@Composable
fun ReaderFastScrollbar(
    listState: LazyListState,
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    val scope = rememberCoroutineScope()

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress, dragging) {
        if (listState.isScrollInProgress || dragging) {
            visible = true
        } else {
            delay(FADE_DELAY_MS)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "fastScrollAlpha")
    if (alpha == 0f) return

    val maxIndex = (pageCount - 1).coerceAtLeast(1)
    val fraction =
        if (dragging) dragFraction else currentPage / maxIndex.toFloat()
    val shownPage =
        if (dragging) (dragFraction * maxIndex).roundToInt() else currentPage

    BoxWithConstraints(modifier = modifier.fillMaxHeight().alpha(alpha)) {
        val density = LocalDensity.current
        val thumbHeightPx = with(density) { THUMB_HEIGHT_DP.dp.roundToPx() }
        val trackHeightPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(1)
        val thumbOffsetPx = (fraction.coerceIn(0f, 1f) * trackHeightPx).roundToInt()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset { IntOffset(0, thumbOffsetPx) },
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(
                    text = "${shownPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .width(THUMB_WIDTH_DP.dp)
                        .height(THUMB_HEIGHT_DP.dp)
                        .pointerInput(pageCount, trackHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragFraction = fraction
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (trackHeightPx > 0) {
                                        dragFraction =
                                            (dragFraction + dragAmount / trackHeightPx).coerceIn(0f, 1f)
                                        val target = (dragFraction * maxIndex).roundToInt()
                                        scope.launch { listState.scrollToItem(target) }
                                    }
                                },
                            )
                        },
            ) {}
        }
    }
}

private const val THUMB_WIDTH_DP = 8
private const val THUMB_HEIGHT_DP = 48
private const val FADE_DELAY_MS = 1_100L
