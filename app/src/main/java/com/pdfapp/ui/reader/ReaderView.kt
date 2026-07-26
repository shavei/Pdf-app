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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect
import com.pdfapp.core.renderer.text.PdfLink
import com.pdfapp.ui.ZoomPreset
import com.pdfapp.ui.common.ReaderSemantics
import com.pdfapp.ui.common.rememberTouchExplorationEnabled
import com.pdfapp.ui.document.PdfEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = viewModel.session ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Page text is only extracted when a screen reader will read it out
    // (mobile-ui-plan Phase F.2) — see [ReaderPage].
    val touchExploration = rememberTouchExplorationEnabled()

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

        // Live pinch transform. During a gesture we do NOT touch `zoom` (which
        // drives the list's measured width/height); instead we scale the
        // already-composed — and therefore already-sharp — content on the GPU
        // via a draw-time graphicsLayer. Reading these three values only in the
        // graphicsLayer block means each pinch frame triggers a draw-only
        // invalidation, never a remeasure/relayout, so the gesture tracks the
        // fingers like the editor's canvas matrix. The transform is baked into
        // `zoom` (and the scroll offsets) once, on gesture end, where the
        // debounced strip renderer then re-rasterises crisp tiles.
        var liveScale by remember(session) { mutableFloatStateOf(1f) }
        var liveTranslation by remember(session) { mutableStateOf(Offset.Zero) }
        var livePivot by remember(session) { mutableStateOf(Offset.Zero) }

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
         * Commit a settled zoom into the layout, keeping the content under
         * [centroid] fixed and applying any live-gesture [pan]: the anchor page
         * keeps its first visible line and the horizontal offset tracks the
         * fingers, like Drive's document-level zoom. This is the expensive,
         * relayout-per-call path, so it runs once when a gesture ends — never
         * per frame. The [pan] is the view-space translation the live transform
         * showed during the gesture (fingers-down positive), folded straight
         * into the new scroll offsets so committing produces no visible jump.
         */
        suspend fun setZoomAnchored(
            target: Float,
            centroid: Offset,
            pan: Offset = Offset.Zero,
        ) {
            val clamped = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
            val k = clamped / zoom
            if (k == 1f && pan == Offset.Zero) {
                // Nothing to bake in, but always drop back to the identity
                // transform so a released gesture cannot leave the layer scaled.
                liveScale = 1f
                liveTranslation = Offset.Zero
                return
            }
            val anchorIndex = listState.firstVisibleItemIndex
            val anchorOffset = listState.firstVisibleItemScrollOffset
            val newOffset = ((anchorOffset + centroid.y) * k - centroid.y - pan.y).roundToInt()
            val newHorizontal = ((hScroll.value + centroid.x) * k - centroid.x - pan.x).roundToInt()
            // Swap layout zoom and the live transform in one snapshot (no
            // suspension between the writes) so recomposition never sees the new
            // zoom with the old preview scale still applied — that would flash a
            // doubled magnification for a frame.
            zoom = clamped
            liveScale = 1f
            liveTranslation = Offset.Zero
            listState.scrollToItem(anchorIndex, max(0, newOffset))
            hScroll.scrollTo(max(0, newHorizontal))
        }

        // Double-tap: animate the cheap live layer toward the target, then bake
        // it in once — so the animation is GPU-smooth like a pinch, not twelve
        // relayouts.
        suspend fun animateZoom(
            target: Float,
            centroid: Offset,
        ) {
            val start = zoom
            val clampedTarget = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
            if (clampedTarget == start) return
            livePivot = centroid
            liveTranslation = Offset.Zero
            val steps = DOUBLE_TAP_ZOOM_STEPS
            repeat(steps) { step ->
                val fraction = (step + 1) / steps.toFloat()
                val z = start + (clampedTarget - start) * fraction
                liveScale = z / start
                delay(DOUBLE_TAP_FRAME_MS)
            }
            setZoomAnchored(clampedTarget, centroid)
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
        // Render a small window of pages ahead of (and just behind) the viewport
        // so a scroll lands on a finished page, not a blank one. `collectLatest`
        // re-prioritises the window the instant the visible range moves, and the
        // debounce means a fast fling — whose range keeps changing — never wastes
        // the single renderer on pages it is about to blow past. The visible
        // pages request themselves via composition, so warming only fills the gap
        // between "in view" and "rendered".
        LaunchedEffect(listState, session) {
            snapshotFlow {
                val info = listState.layoutInfo.visibleItemsInfo
                (info.firstOrNull()?.index ?: 0) to (info.lastOrNull()?.index ?: 0)
            }.collectLatest { (first, last) ->
                delay(PREFETCH_DEBOUNCE_MS)
                for (index in ReaderPrefetch.window(first, last, session.pageCount)) {
                    val size = runCatching { session.cache.pageSize(index) }.getOrNull() ?: continue
                    runCatching { session.cache.page(index, viewportWidthPx / size.widthPt) }
                }
            }
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
                    .documentPinchGestures(
                        onStart = { centroid ->
                            livePivot = centroid
                            liveScale = 1f
                            liveTranslation = Offset.Zero
                        },
                        onPinch = { zoomChange, pan ->
                            // Accumulate into the live transform only, clamped to
                            // the zoom range so the preview never shows more than
                            // the commit will. Pure draw-phase state writes.
                            val clampedZoom = (zoom * liveScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            liveScale = clampedZoom / zoom
                            liveTranslation += pan
                        },
                        onEnd = {
                            val committedScale = liveScale
                            val pivot = livePivot
                            val pan = liveTranslation
                            scope.launch { setZoomAnchored(zoom * committedScale, pivot, pan) }
                        },
                    ).pointerInput(session) {
                        detectTapGestures(
                            onTap = { handleTap(it) },
                            onDoubleTap = { tap ->
                                scope.launch {
                                    animateZoom(ReaderZoom.doubleTapTarget(zoom), tap)
                                }
                            },
                        )
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Clip the scaled overflow to the viewport (without this,
                        // a live zoom would spill over the scrollbar and chrome).
                        .clipToBounds()
                        // The live pinch transform. Reading the three live-* state
                        // values here — and only here — keeps a gesture on the
                        // draw phase: no remeasure of the list, just a GPU
                        // re-composite about the pinch centroid.
                        .graphicsLayer {
                            scaleX = liveScale
                            scaleY = liveScale
                            translationX = liveTranslation.x
                            translationY = liveTranslation.y
                            val w = if (size.width > 0f) size.width else 1f
                            val h = if (size.height > 0f) size.height else 1f
                            transformOrigin = TransformOrigin(livePivot.x / w, livePivot.y / h)
                        }.horizontalScroll(hScroll),
            ) {
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
                            describeText = touchExploration,
                            chromeVisible = chromeVisible,
                            onToggleChrome = onToggleChrome,
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
    describeText: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val session = viewModel.session ?: return
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val pageSize by produceState(viewModel.defaultPageSize, session, pageIndex) {
        value = session.cache.pageSize(pageIndex)
    }

    // The page reads as one node: its position, and — once a screen reader is
    // running — the text on it, so TalkBack can speak a page instead of naming
    // a picture (mobile-ui-plan Phase F.2, plan.md's "expose extracted page
    // text"). Extraction is a PdfBox parse, so it only happens under
    // [describeText]; until it lands the position alone is announced.
    val pageText by produceState<String?>(null, session, pageIndex, describeText) {
        if (!describeText) {
            value = null
            return@produceState
        }
        value =
            runCatching {
                withContext(Dispatchers.IO) { session.textDocument().pageText(pageIndex).text }
            }.getOrNull()
    }
    val pageLabel = ReaderSemantics.pageLabel(pageIndex, session.pageCount, pageText)
    val chromeLabel = ReaderSemantics.chromeToggleLabel(chromeVisible)
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
                    .semantics(mergeDescendants = true) {
                        contentDescription = pageLabel
                        // A screen reader can't perform the reader's background
                        // tap, so the immersive toggle becomes this node's click
                        // action — double-tapping a page hides or shows the
                        // chrome exactly as the sighted gesture does.
                        onClick(label = chromeLabel) {
                            onToggleChrome()
                            true
                        }
                    }.pointerInput(pageIndex, pointScale) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                // Tactile confirmation that the long-press latched
                                // and text selection has begun (Phase D.2).
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    // Described by the merged page node above.
                    contentDescription = null,
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
            // Native inputs over the AcroForm widgets, composed only while the
            // fill layer is on so text fields never contest the reader's own
            // tap, pan and long-press gestures (plan Phase 4).
            FormFieldLayer(viewModel, pageIndex, pointScale, pageSize.heightPt)
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
    onStart: (centroid: Offset) -> Unit,
    onPinch: (zoomChange: Float, pan: Offset) -> Unit,
    onEnd: () -> Unit,
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
                        if (!pinching) {
                            pinching = true
                            // Anchor the whole gesture on the centroid where the
                            // second finger landed; pan tracks it from there.
                            onStart(event.calculateCentroid(useCurrent = true))
                        }
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoomChange != 1f || pan != Offset.Zero) {
                            onPinch(zoomChange, pan)
                        }
                        event.changes.forEach { it.consume() }
                    }
                    // Finishing a pinch with one finger down: swallow its moves
                    // so the document does not lurch as the second finger lifts.
                    pinching -> event.changes.forEach { it.consume() }
                }
            }
            // Lifted the last finger: bake the live transform into the layout.
            if (pinching) onEnd()
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

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM_STEPS = 12
private const val DOUBLE_TAP_FRAME_MS = 16L

// Debounce after the last pinch step before committing crisper strips.
private const val TILE_SETTLE_MS = 180L

// Wait for the scroll to settle briefly before warming off-screen pages, so a
// fast fling doesn't spend the single renderer on pages it's about to pass.
private const val PREFETCH_DEBOUNCE_MS = 100L

// Widest strip bitmap we will ask the renderer for; stays inside the safe
// GPU texture edge of modern devices while allowing full 8x-sharp tiles on
// phone-width viewports.
private const val MAX_RENDER_WIDTH_PX = 8192f
