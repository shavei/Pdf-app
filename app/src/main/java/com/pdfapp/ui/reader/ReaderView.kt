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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import com.pdfapp.core.renderer.text.PdfLink
import com.pdfapp.overlay.ViewportTransform
import com.pdfapp.ui.PdfEditorViewModel
import com.pdfapp.ui.ZoomPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * One-page-at-a-time reader: a [HorizontalPager] whose slots each host a single
 * page laid out inside the same [ViewportTransform] zoom system the overlay
 * editor uses — two fingers pinch-zoom and pan, a single finger pans once the
 * page is zoomed past its fit scale, and swiping between pages is enabled only
 * while a page sits at fit (so a pan gesture never fights the pager). Search-match
 * highlighting, long-press text selection, tappable links, night-mode inversion
 * and crisp high-zoom tiles are preserved.
 */
@Composable
fun ReaderView(
    viewModel: PdfEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val session = viewModel.session ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()

        // A fresh pager per document so the last-read page is restored and stale
        // scroll state never carries across a reopen.
        val pagerState =
            key(session) {
                rememberPagerState(
                    initialPage = viewModel.currentPageIndex.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0)),
                ) { session.pageCount }
            }

        // One-shot navigation requests from search / outline / go-to-page.
        LaunchedEffect(viewModel.pendingReadTarget) {
            viewModel.pendingReadTarget?.let { target ->
                pagerState.scrollToPage(target)
                viewModel.readTargetConsumed()
            }
        }
        // Track the visible page for the indicator and last-read persistence.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { viewModel.onVisiblePageChanged(it) }
        }

        // Paging is disabled while the current page is zoomed in, so a one-finger
        // pan moves the page instead of flipping to the next one. A freshly
        // settled page always starts at fit, so re-enable on every page change.
        var currentZoom by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(pagerState.currentPage) { currentZoom = 1f }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentZoom <= FIT_ZOOM_EPSILON,
            pageSpacing = PAGE_SPACING.dp,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            ReaderPage(
                viewModel = viewModel,
                pageIndex = index,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                isCurrentPage = index == pagerState.currentPage,
                zoomPreset = viewModel.pendingZoomPreset,
                onZoomChanged = { zoom -> if (index == pagerState.currentPage) currentZoom = zoom },
            )
        }
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // value is assigned; lint misreads the suspend producers
private fun ReaderPage(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    isCurrentPage: Boolean,
    zoomPreset: ZoomPreset?,
    onZoomChanged: (Float) -> Unit,
) {
    val session = viewModel.session ?: return
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageSize by produceState(viewModel.defaultPageSize, session, pageIndex) {
        value = session.cache.pageSize(pageIndex)
    }

    // The page bitmap is rendered to fit the viewport width; the viewport
    // transform then fits, zooms and pans it inside the slot. Content space is
    // the bitmap's pixel grid, so overlay/point mapping never depends on zoom.
    val baseScale = viewportWidthPx / pageSize.widthPt
    val contentWidthPx = pageSize.widthPt * baseScale
    val contentHeightPx = pageSize.heightPt * baseScale

    val viewport = remember(pageIndex) { ViewportTransform() }

    // Mirror the (non-observable) transform into Compose state so the graphics
    // layer and tile bucket recompose as the gesture updates the viewport.
    var glScale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var glOffsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var glOffsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    fun syncTransform() {
        glScale = viewport.scale
        glOffsetX = viewport.offsetX
        glOffsetY = viewport.offsetY
    }

    LaunchedEffect(viewportWidthPx, viewportHeightPx, contentWidthPx, contentHeightPx) {
        viewport.setViewSize(viewportWidthPx, viewportHeightPx)
        viewport.setContentSize(contentWidthPx, contentHeightPx)
        syncTransform()
        onZoomChanged(viewport.zoom)
    }

    // Fit-width / fit-page presets from the reader menu (plan 2.6), applied to
    // whichever page is currently showing.
    LaunchedEffect(zoomPreset, isCurrentPage) {
        if (!isCurrentPage || zoomPreset == null) return@LaunchedEffect
        when (zoomPreset) {
            ZoomPreset.FIT_WIDTH -> viewport.setZoom(1f / viewport.fitScale)
            ZoomPreset.FIT_PAGE -> viewport.setZoom(1f)
        }
        syncTransform()
        onZoomChanged(viewport.zoom)
        viewModel.zoomPresetConsumed()
    }

    // Crisp tiles follow a *settled* scale: a live pinch changes the scale every
    // frame, but re-rendering strips each time it crosses an integer level would
    // stutter the gesture. The stretched base bitmap covers the gap until the
    // pinch pauses, then the debounced value commits a sharp bucket.
    var settledScale by remember(pageIndex) { mutableFloatStateOf(1f) }
    LaunchedEffect(glScale) {
        delay(TILE_SETTLE_MS)
        settledScale = glScale
    }
    val bucket = ceil(settledScale.toDouble()).toInt().coerceIn(1, MAX_STRIPS)

    val bitmap by produceState<ImageBitmap?>(null, session, pageIndex, baseScale) {
        value = session.cache.page(pageIndex, baseScale).bitmap.asImageBitmap()
    }

    val pointScale = baseScale

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
        contentAlignment = Alignment.TopStart,
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .zoomPanGestures(viewport) {
                    syncTransform()
                    onZoomChanged(viewport.zoom)
                },
    ) {
        Box(
            modifier =
                Modifier
                    .size(
                        with(density) { contentWidthPx.toDp() },
                        with(density) { contentHeightPx.toDp() },
                    ).graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = glScale
                        scaleY = glScale
                        translationX = glOffsetX
                        translationY = glOffsetY
                    }.background(if (viewModel.nightMode) Color.Black else Color.White)
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
                HighZoomStrips(viewModel, pageIndex, baseScale, bucket)
            }
            PageDecorations(viewModel, pageIndex, pointScale, pageSize.heightPt)
        }
    }
}

/** Crisp tile overlay for zoomed pages: [bucket] bands at [baseScale]×[bucket]. */
@Composable
private fun HighZoomStrips(
    viewModel: PdfEditorViewModel,
    pageIndex: Int,
    baseScale: Float,
    bucket: Int,
) {
    val session = viewModel.session ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(bucket) { strip ->
            val stripBitmap by produceState<ImageBitmap?>(null, session, pageIndex, baseScale, bucket, strip) {
                value =
                    session.cache
                        .strip(pageIndex, baseScale * bucket, strip, bucket)
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
 * Drive [viewport] from raw pointer input the way the overlay editor does: two
 * or more fingers pinch-zoom about — and pan with — their centroid; a single
 * finger pans once the page is zoomed past fit and the drag clears touch slop.
 * A single finger at fit zoom is left unconsumed so it reaches the pager (page
 * flip) and the tap / long-press detectors below. Once a pinch begins the rest
 * of the gesture is consumed even after a finger lifts, so the page never jumps.
 */
private fun Modifier.zoomPanGestures(
    viewport: ViewportTransform,
    onChanged: () -> Unit,
): Modifier =
    pointerInput(Unit) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var pinching = false
            var totalPan = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    pinching = true
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true)
                    if (zoomChange != 1f || pan != Offset.Zero) {
                        viewport.pinch(centroid.x, centroid.y, zoomChange, pan.x, pan.y)
                        onChanged()
                    }
                    event.changes.forEach { it.consume() }
                } else if (pinching) {
                    // Finishing a pinch with one finger down: swallow its moves so
                    // the page does not lurch as the second finger lifts.
                    event.changes.forEach { it.consume() }
                } else if (viewport.zoom > 1f) {
                    // One finger on a zoomed page pans it; yield when a child (text
                    // selection) has already claimed the drag by consuming it.
                    val change = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                    val delta = change?.positionChange() ?: Offset.Zero
                    totalPan += delta
                    if (change != null && totalPan.getDistance() > touchSlop) {
                        viewport.panBy(delta.x, delta.y)
                        onChanged()
                        change.consume()
                    }
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
// Tile sharpness plateaus here: beyond 4× the base tiles upscale rather than
// re-render, keeping per-strip bitmaps within a sane texture/memory budget even
// as the viewport zooms to its 8× ceiling.
private const val MAX_STRIPS = 4
private const val PAGE_SPACING = 8

// Zoom at (or below) this is treated as "fit", so page-flip swiping is allowed.
private const val FIT_ZOOM_EPSILON = 1.001f

// Debounce after the last pinch step before committing a crisper tile bucket.
private const val TILE_SETTLE_MS = 180L
