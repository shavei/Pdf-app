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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import com.pdfapp.core.renderer.text.PdfLink
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.ZoomPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Drive-style continuous reader: all pages stacked in one vertically scrolling
 * [LazyColumn]. Zoom is document-level — pinching (or double-tapping) scales
 * every page and survives scrolling across page boundaries; horizontal panning
 * is a plain [horizontalScroll] once the pages are wider than the viewport.
 * A single tap follows a link or toggles the chrome; long-press starts text
 * selection. Crisp high-zoom tiles are rendered only for the strips actually
 * on screen, so quality never trades off against memory.
 */
@Composable
fun ReaderView(
    viewModel: PdfEditorViewModel,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = viewModel.session ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()

        // Fresh scroll/zoom state per document so the last-read page is
        // restored and nothing leaks across a reopen.
        val listState =
            key(session) {
                rememberLazyListState(
                    initialFirstVisibleItemIndex =
                        viewModel.currentPageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                )
            }
        val hScroll = key(session) { rememberScrollState() }
        var zoom by remember(session) { mutableFloatStateOf(1f) }

        // Crisp tiles follow a *settled* zoom: re-rendering on every pinch frame
        // would stutter, so the stretched base bitmap covers the gap until the
        // gesture pauses and the debounced value commits sharp strips.
        var settledZoom by remember(session) { mutableFloatStateOf(1f) }
        LaunchedEffect(zoom) {
            delay(TILE_SETTLE_MS)
            settledZoom = zoom
        }

        val lazyWidthPx = viewportWidthPx * max(1f, zoom)
        val pageWidthPx = viewportWidthPx * zoom

        // Sharp strips are cut at integer zoom buckets; the ceiling keeps any
        // single strip's width inside a safe GPU texture edge.
        val maxBucket =
            floor(MAX_RENDER_WIDTH_PX / viewportWidthPx.coerceAtLeast(1f))
                .toInt()
                .coerceIn(1, ceil(MAX_ZOOM.toDouble()).toInt())
        val bucket = ceil(settledZoom.toDouble()).toInt().coerceIn(1, maxBucket)

        /**
         * Rescale to [target] keeping the content under [centroid] fixed: the
         * anchor page keeps its first visible line and the horizontal offset
         * tracks the fingers, like Drive's document-level zoom.
         */
        suspend fun setZoomAnchored(
            target: Float,
            centroid: Offset,
        ) {
            val clamped = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
            if (clamped == zoom) return
            val k = clamped / zoom
            val anchorIndex = listState.firstVisibleItemIndex
            val anchorOffset = listState.firstVisibleItemScrollOffset
            val newOffset = ((anchorOffset + centroid.y) * k - centroid.y).roundToInt()
            val newHorizontal = ((hScroll.value + centroid.x) * k - centroid.x).roundToInt()
            zoom = clamped
            listState.scrollToItem(anchorIndex, max(0, newOffset))
            hScroll.scrollTo(max(0, newHorizontal))
        }

        suspend fun animateZoom(
            target: Float,
            centroid: Offset,
        ) {
            val start = zoom
            val steps = DOUBLE_TAP_ZOOM_STEPS
            repeat(steps) { step ->
                val fraction = (step + 1) / steps.toFloat()
                setZoomAnchored(start + (target - start) * fraction, centroid)
                delay(DOUBLE_TAP_FRAME_MS)
            }
        }

        // One-shot navigation requests from search / outline / go-to-page.
        LaunchedEffect(viewModel.pendingReadTarget) {
            viewModel.pendingReadTarget?.let { target ->
                listState.scrollToItem(target)
                viewModel.readTargetConsumed()
            }
        }
        // Track the visible page for the scrollbar and last-read persistence.
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.onVisiblePageChanged(it) }
        }
        // Fit-width / fit-page presets from the reader menu.
        LaunchedEffect(viewModel.pendingZoomPreset) {
            val preset = viewModel.pendingZoomPreset ?: return@LaunchedEffect
            val pageSize = session.cache.pageSize(listState.firstVisibleItemIndex)
            val target =
                when (preset) {
                    ZoomPreset.FIT_WIDTH -> 1f
                    ZoomPreset.FIT_PAGE ->
                        (viewportHeightPx / (viewportWidthPx * (pageSize.heightPt / pageSize.widthPt)))
                            .coerceAtMost(1f)
                }
            setZoomAnchored(target, Offset(viewportWidthPx / 2f, viewportHeightPx / 2f))
            viewModel.zoomPresetConsumed()
        }

        /** Resolve a viewport tap to a page point; follow a link or toggle chrome. */
        fun handleTap(tap: Offset) {
            viewModel.selectionController.clear()
            val item =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { tap.y >= it.offset && tap.y < it.offset + it.size }
            if (item == null) {
                onToggleChrome()
                return
            }
            scope.launch {
                val pageSize = session.cache.pageSize(item.index)
                val pointScale = pageWidthPx / pageSize.widthPt
                val centering = max(0f, (lazyWidthPx - pageWidthPx) / 2f)
                val xPt = (hScroll.value + tap.x - centering) / pointScale
                val yPt = pageSize.heightPt - (tap.y - item.offset) / pointScale
                val link =
                    runCatching { session.links(item.index) }.getOrNull()
                        ?.firstOrNull { it.box.contains(PdfPoint(xPt, yPt)) }
                when (val target = link?.target) {
                    is PdfLink.Target.Page -> viewModel.goToPage(target.pageIndex)
                    is PdfLink.Target.Url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                        }
                    null -> onToggleChrome()
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .documentPinchGestures { zoomChange, pan, centroid ->
                        scope.launch {
                            if (zoomChange != 1f) setZoomAnchored(zoom * zoomChange, centroid)
                            if (pan != Offset.Zero) {
                                listState.scrollBy(-pan.y)
                                hScroll.scrollBy(-pan.x)
                            }
                        }
                    }.pointerInput(session) {
                        detectTapGestures(
                            onTap = { handleTap(it) },
                            onDoubleTap = { tap ->
                                scope.launch {
                                    animateZoom(if (zoom > FIT_ZOOM_EPSILON) 1f else DOUBLE_TAP_ZOOM, tap)
                                }
                            },
                        )
                    },
        ) {
            Box(modifier = Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(PAGE_SPACING.dp),
                    modifier =
                        Modifier
                            .width(with(density) { lazyWidthPx.toDp() })
                            .fillMaxHeight(),
                ) {
                    items(count = session.pageCount, key = { it }) { index ->
                        ReaderPage(
                            viewModel = viewModel,
                            listState = listState,
                            pageIndex = index,
                            pageWidthPx = pageWidthPx,
                            viewportWidthPx = viewportWidthPx,
                            viewportHeightPx = viewportHeightPx,
                            bucket = bucket,
                        )
                    }
                }
            }
            ReaderFastScrollbar(
                listState = listState,
                pageCount = session.pageCount,
                currentPage = viewModel.currentPageIndex,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * One page of the continuous reader: the fit-width base bitmap stretched to the
 * zoomed layout size, sharp on-screen strips above it, and the search/selection/
 * overlay decorations. The page box is centered so zoomed-out pages float in
 * the middle of the viewport.
 */
@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the suspend producers
private fun ReaderPage(
    viewModel: PdfEditorViewModel,
    listState: LazyListState,
    pageIndex: Int,
    pageWidthPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    bucket: Int,
) {
    val session = viewModel.session ?: return
    val density = LocalDensity.current

    val pageSize by produceState(viewModel.defaultPageSize, session, pageIndex) {
        value = session.cache.pageSize(pageIndex)
    }
    val pageHeightPx = pageWidthPx * (pageSize.heightPt / pageSize.widthPt)
    val pointScale = pageWidthPx / pageSize.widthPt

    // The base bitmap renders at fit-width device resolution regardless of
    // zoom, so a live pinch stretches it instead of thrashing the renderer.
    val baseScale = viewportWidthPx / pageSize.widthPt
    val bitmap by produceState<ImageBitmap?>(null, session, pageIndex, baseScale) {
        value = session.cache.page(pageIndex, baseScale).bitmap.asImageBitmap()
    }

    fun toPdfPoint(offset: Offset) = PdfPoint(offset.x / pointScale, pageSize.heightPt - offset.y / pointScale)

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            Modifier
                .width(with(density) { max(pageWidthPx, viewportWidthPx).toDp() })
                .height(with(density) { pageHeightPx.toDp() }),
    ) {
        Box(
            modifier =
                Modifier
                    .width(with(density) { pageWidthPx.toDp() })
                    .fillMaxHeight()
                    .background(if (viewModel.nightMode) Color.Black else Color.White)
                    .pointerInput(pageIndex, pointScale) {
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
                    filterQuality = FilterQuality.High,
                    colorFilter = if (viewModel.nightMode) NIGHT_FILTER else null,
                )
            }
            if (bucket > 1) {
                VisibleZoomStrips(
                    viewModel = viewModel,
                    listState = listState,
                    pageIndex = pageIndex,
                    baseScale = baseScale,
                    bucket = bucket,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    viewportHeightPx = viewportHeightPx,
                )
            }
            PageDecorations(viewModel, pageIndex, pointScale, pageSize.heightPt)
        }
    }
}

/**
 * Sharp tiles for a zoomed page, rendered at [baseScale]×[bucket] — but only
 * the strips currently intersecting the viewport (plus half a strip of margin),
 * so an 8× zoom never rasterises the parts of the page nobody is looking at.
 */
@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the suspend producers
private fun VisibleZoomStrips(
    viewModel: PdfEditorViewModel,
    listState: LazyListState,
    pageIndex: Int,
    baseScale: Float,
    bucket: Int,
    pageWidthPx: Float,
    pageHeightPx: Float,
    viewportHeightPx: Float,
) {
    val session = viewModel.session ?: return
    val density = LocalDensity.current

    // The item's live offset in the list viewport drives which strips exist.
    val itemTop by remember(listState, pageIndex) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == pageIndex }
                ?.offset
        }
    }
    val top = itemTop ?: return
    val stripHeightPx = pageHeightPx / bucket
    if (stripHeightPx <= 0f) return
    val margin = stripHeightPx / 2f
    val firstStrip = floor((-top - margin) / stripHeightPx).toInt().coerceAtLeast(0)
    val lastStrip =
        (ceil((viewportHeightPx - top + margin) / stripHeightPx).toInt() - 1)
            .coerceAtMost(bucket - 1)
    if (firstStrip > lastStrip) return

    for (strip in firstStrip..lastStrip) {
        key(strip) {
            val stripBitmap by produceState<ImageBitmap?>(null, session, pageIndex, baseScale, bucket, strip) {
                value =
                    session.cache
                        .strip(pageIndex, baseScale * bucket, strip, bucket)
                        .asImageBitmap()
            }
            stripBitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    colorFilter = if (viewModel.nightMode) NIGHT_FILTER else null,
                    modifier =
                        Modifier
                            .offset { IntOffset(0, (strip * stripHeightPx).roundToInt()) }
                            .width(with(density) { pageWidthPx.toDp() })
                            .height(with(density) { stripHeightPx.toDp() }),
                )
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
 * Intercept multi-touch on the initial pass so a pinch is never half-eaten by
 * the scroll containers: two or more fingers zoom about — and pan with — their
 * centroid, and the rest of that gesture stays consumed even after a finger
 * lifts. Single-finger events pass through untouched to the vertical list,
 * the horizontal scroll, and the tap/long-press detectors.
 */
private fun Modifier.documentPinchGestures(
    onPinch: (zoomChange: Float, pan: Offset, centroid: Offset) -> Unit,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                when {
                    pressed >= 2 -> {
                        pinching = true
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoomChange != 1f || pan != Offset.Zero) {
                            onPinch(zoomChange, pan, event.calculateCentroid(useCurrent = true))
                        }
                        event.changes.forEach { it.consume() }
                    }
                    // Finishing a pinch with one finger down: swallow its moves
                    // so the document does not lurch as the second finger lifts.
                    pinching -> event.changes.forEach { it.consume() }
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

private const val PAGE_SPACING = 8

// Zoom at (or below) this is treated as "fit width".
private const val FIT_ZOOM_EPSILON = 1.001f

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val DOUBLE_TAP_ZOOM_STEPS = 12
private const val DOUBLE_TAP_FRAME_MS = 16L

// Debounce after the last pinch step before committing crisper strips.
private const val TILE_SETTLE_MS = 180L

// Widest strip bitmap we will ask the renderer for; stays inside the safe
// GPU texture edge of modern devices while allowing full 8x-sharp tiles on
// phone-width viewports.
private const val MAX_RENDER_WIDTH_PX = 8192f
