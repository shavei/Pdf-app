package com.pdfapp.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Continuous-scroll reader (plan 2.1): a lazy column of on-demand rendered
 * pages with focal-anchored pinch-zoom and two-finger pan, search-match
 * highlighting, long-press text selection, tappable links, and night-mode page
 * inversion.
 *
 * Zoom drives the page width; the horizontal scroll container and the lazy list
 * carry pan (and their own fling) for single-finger gestures, while a two-finger
 * gesture zooms about — and pans with — its centroid so the content under the
 * fingers stays put.
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
        val density = LocalDensity.current
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val hScrollState = rememberScrollState()
        var zoom by remember { mutableFloatStateOf(1f) }

        // Crisp tiles follow a *settled* zoom: a live pinch changes [zoom] every
        // frame, but re-rendering strips each time it crosses an integer level
        // would stutter the gesture. The stretched base bitmap covers the gap
        // until the pinch pauses, then the debounced value commits a sharp bucket.
        var tileZoom by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(zoom) {
            delay(TILE_SETTLE_MS)
            tileZoom = zoom
        }

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
        val spacingPx = with(density) { PAGE_SPACING.dp.toPx() }
        val pageAspect = viewModel.defaultPageSize.heightPt / viewModel.defaultPageSize.widthPt

        // One pinch/two-finger-pan step: zoom about the gesture centroid and pan
        // with it, keeping the content under the fingers fixed on both axes. The
        // horizontal scroll container anchors exactly; the lazy list is anchored
        // from an estimate of the absolute scroll (uniform page stride).
        fun onZoomPan(
            centroid: Offset,
            pan: Offset,
            zoomChange: Float,
        ) {
            val old = zoom
            val newZoom = (old * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val ratio = newZoom / old
            val hOffset =
                ReaderZoomMath.horizontalOffset(hScrollState.value.toFloat(), centroid.x, ratio, pan.x)
            val stridePx = viewportWidthPx * old * pageAspect + spacingPx
            val vScroll =
                listState.firstVisibleItemIndex * stridePx + listState.firstVisibleItemScrollOffset
            val vDelta = ReaderZoomMath.verticalDelta(vScroll, centroid.y, ratio, pan.y)
            zoom = newZoom
            hScrollState.dispatchRawDelta(hOffset - hScrollState.value.toFloat())
            listState.dispatchRawDelta(vDelta)
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .zoomAndPan(onGesture = ::onZoomPan)
                    .horizontalScroll(hScrollState),
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(PAGE_SPACING.dp),
                modifier =
                    Modifier.width(with(density) { pageWidthPx.toDp() }),
            ) {
                items(count = session.pageCount, key = { it }) { index ->
                    ReaderPage(
                        viewModel = viewModel,
                        pageIndex = index,
                        pageWidthPx = pageWidthPx,
                        viewportWidthPx = viewportWidthPx,
                        tileZoom = tileZoom,
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
    tileZoom: Float,
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
    val bucket = ceil(tileZoom.toDouble()).toInt().coerceIn(1, MAX_STRIPS)
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

/**
 * Two-or-more-finger pinch/pan: reports the gesture centroid, the frame's pan
 * delta and its zoom factor so the caller can zoom about — and pan with — the
 * fingers. Single-finger gestures fall through untouched to the underlying
 * scroll containers, preserving their scroll and fling. Once a pinch begins the
 * rest of the gesture is consumed (even after a finger lifts) so the list does
 * not jump.
 */
private fun Modifier.zoomAndPan(onGesture: (Offset, Offset, Float) -> Unit): Modifier =
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
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        onGesture(event.calculateCentroid(useCurrent = true), pan, zoom)
                    }
                    event.changes.forEach { it.consume() }
                } else if (pinching) {
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

// Debounce after the last pinch step before committing a crisper tile bucket.
private const val TILE_SETTLE_MS = 180L
