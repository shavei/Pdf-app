package com.pdfapp.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import com.pdfapp.core.renderer.text.PdfLink
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.ZoomPreset
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Continuous-scroll reader (plan 2.1): a lazy column of on-demand rendered
 * pages with pinch-zoom, search-match highlighting, long-press text
 * selection, tappable links, and night-mode page inversion.
 */
@Composable
fun ReaderView(
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val session = viewModel.session ?: return
    val listState = viewModel.readerListState

    // One-shot navigation requests from search / outline / go-to-page.
    LaunchedEffect(viewModel.pendingReadTarget) {
        viewModel.pendingReadTarget?.let { target ->
            listState.scrollToItem(target)
            viewModel.readTargetConsumed()
        }
    }
    // Track the visible page for the indicator and last-read persistence.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.onVisiblePageChanged(it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        var zoom by remember { mutableFloatStateOf(1f) }
        // Fit-width / fit-page presets from the reader menu (plan 2.6).
        LaunchedEffect(viewModel.pendingZoomPreset) {
            val preset = viewModel.pendingZoomPreset ?: return@LaunchedEffect
            zoom =
                when (preset) {
                    ZoomPreset.FIT_WIDTH -> 1f
                    ZoomPreset.FIT_PAGE ->
                        minOf(
                            1f,
                            viewportHeightPx * viewModel.defaultPageSize.widthPt /
                                (viewportWidthPx * viewModel.defaultPageSize.heightPt),
                        )
                }.coerceIn(MIN_ZOOM, MAX_ZOOM)
            viewModel.zoomPresetConsumed()
        }
        val pageWidthPx = viewportWidthPx * zoom
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .pinchZoom { change -> zoom = (zoom * change).coerceIn(MIN_ZOOM, MAX_ZOOM) }
                    .horizontalScroll(rememberScrollState()),
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(PAGE_SPACING.dp),
                modifier =
                    Modifier.width(with(LocalDensity.current) { pageWidthPx.toDp() }),
            ) {
                items(count = session.pageCount, key = { it }) { index ->
                    ReaderPage(
                        viewModel = viewModel,
                        pageIndex = index,
                        pageWidthPx = pageWidthPx,
                        viewportWidthPx = viewportWidthPx,
                        zoom = zoom,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the suspend producers
private fun ReaderPage(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    pageWidthPx: Float,
    viewportWidthPx: Float,
    zoom: Float,
) {
    val session = viewModel.session ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageSize by produceState(viewModel.defaultPageSize, session, pageIndex) {
        value = session.cache.pageSize(pageIndex)
    }
    // The base bitmap is always the cheap fit-width render. Zoomed past 1x it
    // stays visible (stretched) while crisp tiles arrive on top: the page is
    // cut into ceil(zoom) horizontal strips rendered at the zoom's scale, so
    // no single high-zoom bitmap ever exceeds roughly a screen in size (2.8).
    val fitScale = viewportWidthPx / pageSize.widthPt
    val bucket = ceil(zoom.toDouble()).toInt().coerceIn(1, MAX_STRIPS)
    val bitmap by produceState<ImageBitmap?>(null, session, pageIndex, fitScale) {
        value = session.cache.page(pageIndex, fitScale).bitmap.asImageBitmap()
    }

    val pointScale = pageWidthPx / pageSize.widthPt

    fun toPdfPoint(offset: Offset) = PdfPoint(offset.x / pointScale, pageSize.heightPt - offset.y / pointScale)

    fun handleTap(point: PdfPoint) {
        viewModel.selectionController.clear()
        scope.launch {
            val link =
                runCatching { session.links(pageIndex) }.getOrNull()
                    ?.firstOrNull { it.box.contains(point) } ?: return@launch
            when (val target = link.target) {
                is PdfLink.Target.Page -> viewModel.goToPage(target.pageIndex)
                is PdfLink.Target.Url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                    }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(pageSize.widthPt / pageSize.heightPt)
                .background(if (viewModel.nightMode) Color.Black else Color.White)
                .pointerInput(pageIndex, pointScale) {
                    detectTapGestures(onTap = { handleTap(toPdfPoint(it)) })
                }.pointerInput(pageIndex, pointScale) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            viewModel.selectionController.startAt(pageIndex, toPdfPoint(offset))
                        },
                        onDrag = { change, _ ->
                            viewModel.selectionController.extendTo(toPdfPoint(change.position))
                        },
                    )
                },
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (viewModel.nightMode) NIGHT_FILTER else null,
            )
        }
        if (bucket > 1) {
            HighZoomStrips(viewModel, pageIndex, fitScale, bucket)
        }
        PageDecorations(viewModel, pageIndex, pointScale, pageSize.heightPt)
    }
}

/** Crisp tile overlay for zoomed pages: [bucket] bands at [fitScale]×[bucket]. */
@Composable
private fun HighZoomStrips(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    fitScale: Float,
    bucket: Int,
) {
    val session = viewModel.session ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(bucket) { strip ->
            val stripBitmap by produceState<ImageBitmap?>(null, session, pageIndex, fitScale, bucket, strip) {
                value =
                    session.cache
                        .strip(pageIndex, fitScale * bucket, strip, bucket)
                        .asImageBitmap()
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                stripBitmap?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = if (viewModel.nightMode) NIGHT_FILTER else null,
                    )
                }
            }
        }
    }
}

/** Search-match, selection and user-overlay painting above the page bitmap. */
@Composable
private fun PageDecorations(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    pointScale: Float,
    pageHeightPt: Float,
) {
    val matches = viewModel.searchController.matchesByPage[pageIndex].orEmpty()
    val currentMatch = viewModel.searchController.current
    val selection =
        viewModel.selectionController.selection?.takeIf { it.pageIndex == pageIndex }
    val overlayLayer = viewModel.overlayDocument?.layerFor(pageIndex)

    fun PdfRect.toTopLeft() = Offset(left * pointScale, (pageHeightPt - top) * pointScale)

    fun PdfRect.toSize() = Size(width * pointScale, height * pointScale)

    Canvas(modifier = Modifier.fillMaxSize()) {
        matches.forEach { match ->
            val color = if (match === currentMatch) CURRENT_MATCH_COLOR else MATCH_COLOR
            match.boxes.forEach { box ->
                drawRect(color = color, topLeft = box.toTopLeft(), size = box.toSize())
            }
        }
        selection?.boxes?.forEach { box ->
            drawRect(color = SELECTION_COLOR, topLeft = box.toTopLeft(), size = box.toSize())
        }
        overlayLayer?.let { drawOverlayLayer(it, pointScale, pageHeightPt) }
    }
}

/** Two-plus fingers zoom the reader; single-finger gestures stay untouched. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    pinching = true
                    val change = event.calculateZoom()
                    if (change != 1f) onZoom(change)
                    event.changes.forEach { it.consume() }
                } else if (pinching) {
                    // A finger lifted mid-pinch: swallow the tail of the gesture
                    // so the list does not jump.
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

private val NIGHT_FILTER =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

private val MATCH_COLOR = Color(0x66FFEB3B)
private val CURRENT_MATCH_COLOR = Color(0x99FF9800)
private val SELECTION_COLOR = Color(0x552196F3)
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 4f
private const val MAX_STRIPS = 4
private const val PAGE_SPACING = 8
