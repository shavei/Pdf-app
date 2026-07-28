package com.pdfapp.e2e

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.pdfapp.MainActivity
import com.pdfapp.ui.common.ReaderSemantics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reader's viewport gestures, on a device.
 *
 * A double-tap brings what you tapped to the middle of the screen; a pinch
 * pins its centroid, since there the fingers say where the content should stay.
 * The cases here guard both, plus a single drag that pans horizontally *and*
 * scrolls vertically, which nested scroll containers cannot do because they
 * lock a drag to one axis.
 *
 * Where a case can, it asserts separations rather than absolute positions. The
 * reader is inset from the window by chrome that varies with the device, so a
 * test that models where it sits is only as good as that model — and the two
 * are hard to tell apart in a failure. A distance between pages needs no such
 * model. Positions come from the page node's unclipped position in the root.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPanE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    /** The page-1 node; its content description carries the document's page count. */
    private var pageLabel: String = ""

    private fun pageNode() = composeRule.onNodeWithContentDescription(pageLabel).fetchSemanticsNode()

    // `positionInRoot`, not `boundsInRoot`: bounds are clipped to the viewport,
    // so a page panned off the left edge reports 0 instead of how far it moved —
    // which is exactly the quantity under test.
    private fun pageLeft(): Float = pageNode().positionInRoot.x

    private fun pageTop(): Float = pageNode().positionInRoot.y

    private fun pageWidth(): Int = pageNode().size.width

    private fun viewportSize(): Pair<Float, Float> =
        composeRule.onRoot().fetchSemanticsNode().size.let { it.width.toFloat() to it.height.toFloat() }

    /** Every currently composed page of a [pageCount]-page document, by its top. */
    private fun composedPageTops(pageCount: Int): Map<Int, Float> =
        (0 until pageCount).mapNotNull { index ->
            composeRule
                .onAllNodesWithContentDescription(ReaderSemantics.pageLabel(index, pageCount))
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.let { index to it.positionInRoot.y }
        }.toMap()

    /**
     * The longest run of consecutive pages whose tops grow with their index.
     *
     * A page that has just scrolled out of view can linger in the lazy list's
     * reuse pool still reporting its last placement — stale by exactly the
     * scroll that displaced it, and so out of order with the pages that really
     * are on screen. Placed pages are always in index order; the reuse pool
     * holds a couple of pages at most, never a longer run than the viewport.
     */
    private fun placedRun(tops: Map<Int, Float>): List<Int> {
        var best = emptyList<Int>()
        var run = mutableListOf<Int>()
        for (index in tops.keys.sorted()) {
            val continues =
                run.isNotEmpty() &&
                    index == run.last() + 1 &&
                    tops.getValue(index) > tops.getValue(run.last())
            if (!continues) run = mutableListOf()
            run.add(index)
            if (run.size > best.size) best = run.toList()
        }
        return best
    }

    /** Distance from one page's top to the next, averaged over a placed run. */
    private fun pagePitch(
        tops: Map<Int, Float>,
        run: List<Int>,
    ): Float = (tops.getValue(run.last()) - tops.getValue(run.first())) / (run.last() - run.first())

    /** Open [pageCount] blank [pageSize] pages in the reader and wait for page 1. */
    private fun withReader(
        pageCount: Int,
        pageSize: PDRectangle = PDRectangle.A4,
        body: () -> Unit,
    ) {
        pageLabel = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = pageCount)
        val pdf = File.createTempFile("readerpan", ".pdf", context.cacheDir)
        try {
            PDDocument().use { doc ->
                repeat(pageCount) { doc.addPage(PDPage(pageSize)) }
                doc.save(pdf)
            }
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(Uri.fromFile(pdf), "application/pdf")
                }
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(pageLabel)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                body()
            }
        } finally {
            pdf.delete()
        }
    }

    /**
     * The left edge of a page the list has really placed, in the root.
     *
     * Every page shares the document's width, centring and pan, so any placed
     * one reports the same x — which is what lets a before/after comparison use
     * whichever pages happen to be on screen at the time. [pageLeft] cannot: it
     * asks for page 1 by name, and a test that has scrolled into the document
     * does not have a page 1 any more.
     */
    private fun placedPageLeft(pageCount: Int): Float {
        val placed = placedRun(composedPageTops(pageCount)).firstOrNull() ?: return 0f
        return composeRule
            .onAllNodesWithContentDescription(ReaderSemantics.pageLabel(placed, pageCount))
            .fetchSemanticsNodes()
            .first()
            .positionInRoot.x
    }

    /**
     * The layout width of a page the list has really placed, at today's zoom.
     *
     * Every page shares the document's zoom, but a page in the reuse pool still
     * reports the size it was last laid out at — a whole zoom out of date. So
     * this asks one of the pages [placedRun] vouches for.
     */
    private fun placedPageWidth(pageCount: Int): Float {
        val placed = placedRun(composedPageTops(pageCount)).firstOrNull() ?: return 0f
        val node =
            composeRule
                .onAllNodesWithContentDescription(ReaderSemantics.pageLabel(placed, pageCount))
                .fetchSemanticsNodes()
                .first()
        return node.size.width.toFloat()
    }

    /** Double-tap at ([x], [y]) as fractions of the viewport; wait for [settled]. */
    private fun doubleTapAndSettle(
        x: Float,
        y: Float,
        settled: () -> Boolean,
    ) {
        val (width, height) = viewportSize()
        composeRule.onRoot().performTouchInput {
            doubleClick(Offset(width * x, height * y))
        }
        composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS, condition = settled)
        composeRule.waitForIdle()
    }

    /**
     * Double-tap at ([x], [y]) as fractions of the viewport, let the zoom
     * settle, and report the factor the page actually grew by.
     */
    private fun doubleTapAt(
        x: Float,
        y: Float = 0.5f,
    ): Float {
        val fitWidth = pageWidth()
        doubleTapAndSettle(x, y) { pageWidth() > fitWidth * ZOOM_IN_THRESHOLD }
        return pageWidth().toFloat() / fitWidth
    }

    @Test
    fun doubleTap_bringsTheTappedPointToTheMiddle() {
        withReader(pageCount = 1) {
            val (width, _) = viewportSize()
            assertThat(pageLeft()).isWithin(TOLERANCE_PX).of(0f)

            // Horizontally the reader spans the whole window, so the landing
            // point is a number this test knows exactly — unlike the vertical
            // axis, where the reader is inset by chrome. That makes this the one
            // place the centring can be asserted against arithmetic rather than
            // against itself.
            //
            // Tapping TAP_X across at 2.5x: the tapped content point sits at
            // TAP_X x width x 2.5, and bringing it to the middle leaves the
            // viewport's left edge that far along, less half a screen. Pinning
            // it under the finger instead — the old behaviour — would subtract a
            // whole TAP_X x width, landing the page a good fraction of a
            // screen further left. The two are far enough apart that no
            // tolerance hides the difference.
            val zoom = doubleTapAt(x = TAP_X)
            val expectedPan = TAP_X * width * zoom - width / 2f

            assertWithMessage("width $width, zoom $zoom")
                .that(pageLeft())
                .isWithin(width * LANDING_TOLERANCE_FRACTION)
                .of(-expectedPan)
        }
    }

    @Test
    fun doubleTapLandsOnTheSameLine_whereverOnThePageYouTap() {
        // Centring, asserted without knowing where the reader sits in the
        // window — which this test cannot know, since the reader is inset by
        // chrome by an amount that varies with the device.
        //
        // Work in root coordinates and let R be the reader's unknown top, L its
        // unknown landing point. A tap at root y=Y has reader-local focus Y-R,
        // so a page top maps to R + L + (topBefore - Y) x zoom: the two unknowns
        // only ever appear as the sum R+L. That sum is computable from measured
        // numbers, and centring means it does not depend on Y. Pinning would
        // make it exactly Y — so tapping at two heights and comparing tells the
        // two behaviours apart without either being modelled.
        withReader(pageCount = 3, pageSize = SHORT_PAGE) {
            val (width, height) = viewportSize()

            fun landingAfterTapAt(yFraction: Float): Float {
                val topBefore = pageTop()
                val fitWidth = pageWidth()
                val zoom = doubleTapAt(x = 0.5f, y = yFraction)
                val landing = pageTop() - (topBefore - height * yFraction) * zoom
                // Back to fit-width, so the next measurement starts where this
                // one did rather than compounding.
                doubleTapAndSettle(0.5f, yFraction) { pageWidth() <= fitWidth + TOLERANCE_PX }
                return landing
            }

            val high = landingAfterTapAt(ZOOM_IN_Y)
            val low = landingAfterTapAt(ZOOM_OUT_Y)

            assertWithMessage(
                "root ${width}x$height; landing from y=$ZOOM_IN_Y was $high, " +
                    "from y=$ZOOM_OUT_Y was $low; pinning would give " +
                    "${height * ZOOM_IN_Y} and ${height * ZOOM_OUT_Y}",
            ).that(low)
                .isWithin(height * TOP_TOLERANCE_FRACTION)
                .of(high)
        }
    }

    @Test
    fun doubleTapOnAShortPage_scalesAboutThatLine() {
        // Landscape-ish pages, so a tap at the middle of the screen asks for a
        // scroll offset taller than the un-zoomed page but shorter than the
        // zoomed one — exactly the case a forced remeasure used to mismeasure
        // against pre-zoom pages, rolling the anchor into a later page and
        // leaving it there. (An upright page needs a tap near the bottom edge
        // to reach that offset, which is chrome territory.)
        withReader(pageCount = ROLLOVER_PAGE_COUNT, pageSize = SHORT_PAGE) {
            val (width, height) = viewportSize()
            val before = composedPageTops(ROLLOVER_PAGE_COUNT)

            val zoom = doubleTapAt(x = 0.5f)
            val after = composedPageTops(ROLLOVER_PAGE_COUNT)

            // Distances between pages, not absolute positions: where the zoom
            // lands is the centring test's business, and unlike a position a
            // separation needs no model of the reader's inset. A rollover into
            // the wrong page breaks the relationship outright — the anchor page
            // ends up somewhere its neighbours' spacing cannot explain — so this
            // still catches what the case was written for.
            val common = placedRun(before).intersect(placedRun(after).toSet()).sorted()
            val diagnostics =
                "root ${width}x$height, zoom $zoom, before $before, after $after"
            assertWithMessage(diagnostics).that(common.size).isAtLeast(2)

            val first = common.first()
            common.drop(1).forEach { index ->
                assertWithMessage("page $index; $diagnostics")
                    .that(after.getValue(index) - after.getValue(first))
                    .isWithin(height * TOP_TOLERANCE_FRACTION)
                    .of((before.getValue(index) - before.getValue(first)) * zoom)
            }
        }
    }

    @Test
    fun doubleTapZoomOut_travelsBackAboveTheAnchorPage() {
        // The zoom-out half of the anchor, and the half a scroll *offset*
        // cannot express. Vertically the reader's position is measured from the
        // top of whichever page is first visible, so a line held near the
        // bottom of the screen while the document shrinks around it belongs on
        // a page further up — a negative offset, which pins to zero and drops
        // the reader at the top of the page it happened to be sitting on
        // instead. That is most of the way through every double-tap back to
        // fit-width, and it needs pages above the viewport to show up at all,
        // which is why this starts halfway through the document.
        withReader(pageCount = SCROLLED_PAGE_COUNT, pageSize = STRIP_PAGE) {
            val (width, height) = viewportSize()

            // Through the list's own scroll action rather than a swipe: a swipe
            // has to start somewhere, and the far end of the screen — where a
            // long one has to start — is chrome. This lands on a known page
            // with the reader at its top, so what follows is arithmetic rather
            // than wherever a fling happened to stop.
            composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(START_PAGE)
            composeRule.waitForIdle()
            val fitWidth = placedPageWidth(SCROLLED_PAGE_COUNT)
            // Nothing below can mean anything if the reader is still at the top
            // of the document: there, both the anchored scroll and the pinned
            // one stop at page 1, and the case never arises.
            val startedAt = placedRun(composedPageTops(SCROLLED_PAGE_COUNT)).first()
            assertWithMessage("reader did not scroll into the document")
                .that(startedAt)
                .isAtLeast(START_PAGE - 1)

            // Zoom in high on the screen, which leaves the reader only a little
            // way into a page — so the way back out has to cross that page's
            // top rather than stopping at it.
            doubleTapAndSettle(x = 0.5f, y = ZOOM_IN_Y) {
                placedPageWidth(SCROLLED_PAGE_COUNT) > fitWidth * ZOOM_IN_THRESHOLD
            }
            val zoomedWidth = placedPageWidth(SCROLLED_PAGE_COUNT)
            val topsBefore = composedPageTops(SCROLLED_PAGE_COUNT)

            // Zoom back out low on the screen. Anchored, every content point
            // maps y -> focusY + (y - focusY) * k about that line.
            val focusY = height * ZOOM_OUT_Y
            doubleTapAndSettle(x = 0.5f, y = ZOOM_OUT_Y) {
                placedPageWidth(SCROLLED_PAGE_COUNT) < zoomedWidth / ZOOM_IN_THRESHOLD
            }
            val topsAfter = composedPageTops(SCROLLED_PAGE_COUNT)
            val k = placedPageWidth(SCROLLED_PAGE_COUNT) / zoomedWidth

            // Only pages both screenfuls placed: a page the reuse pool is
            // holding reports where it last was, not where it is.
            val common = placedRun(topsBefore).intersect(placedRun(topsAfter).toSet())
            val diagnostics =
                "root ${width}x$height, focusY $focusY, zoom out $k, " +
                    "before $topsBefore, after $topsAfter"
            // As above, separations rather than positions: a zoom-out that
            // stopped at the anchor page's top instead of travelling above it
            // leaves the pages it did reach spaced by something other than k,
            // which this catches without modelling where the reader sits.
            val ordered = common.sorted()
            assertWithMessage(diagnostics).that(ordered.size).isAtLeast(2)
            val first = ordered.first()
            ordered.drop(1).forEach { index ->
                assertWithMessage("page $index; $diagnostics")
                    .that(topsAfter.getValue(index) - topsAfter.getValue(first))
                    .isWithin(height * TOP_TOLERANCE_FRACTION)
                    .of((topsBefore.getValue(index) - topsBefore.getValue(first)) * k)
            }
        }
    }

    @Test
    fun aBurstOfDoubleTaps_settlesWhereTheSameTapsDoneSlowlyWould() {
        // What the burst has to preserve is no longer "the layout comes home" —
        // centring means zooming in about a point and back out about the same
        // point does not return you to the start unless that point was already
        // the landing. What must hold is that hurrying changes nothing: two
        // double-taps injected in one gesture, so the second lands inside the
        // first's 200ms animation, have to settle exactly where the same two
        // taps do when each is allowed to finish.
        //
        // That is the property the race broke. The anchor was a single state
        // slot applied by an effect keyed on it, so committing the second
        // cancelled the first's scroll part-way and threw away the travel it
        // still owed; the document rested somewhere neither tap asked for, and
        // tapping again to correct it only stacked another race on top. Every
        // other case here taps once and waits, and cannot see it.
        withReader(pageCount = SCROLLED_PAGE_COUNT, pageSize = STRIP_PAGE) {
            val (width, height) = viewportSize()
            val point = Offset(width * TAP_X, height * BURST_Y)

            fun settleAtFit(fit: Float) =
                composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS) {
                    // Positive as well as back at fit: an empty placed run
                    // reports a width of 0, which would satisfy "no wider than
                    // fit" with no page on screen to say so.
                    val w = placedPageWidth(SCROLLED_PAGE_COUNT)
                    w > 0f && w <= fit + TOLERANCE_PX
                }

            // Reference: in and out, each allowed to finish.
            composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(START_PAGE)
            composeRule.waitForIdle()
            val fitWidth = placedPageWidth(SCROLLED_PAGE_COUNT)
            doubleTapAndSettle(TAP_X, BURST_Y) {
                placedPageWidth(SCROLLED_PAGE_COUNT) > fitWidth * ZOOM_IN_THRESHOLD
            }
            doubleTapAndSettle(TAP_X, BURST_Y) {
                settleAtFit(fitWidth)
                true
            }
            val slow = composedPageTops(SCROLLED_PAGE_COUNT)
            val slowLeft = placedPageLeft(SCROLLED_PAGE_COUNT)

            // Same two taps, one gesture.
            composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(START_PAGE)
            composeRule.waitForIdle()
            composeRule.onRoot().performTouchInput {
                doubleClick(point)
                doubleClick(point)
            }
            settleAtFit(fitWidth)
            composeRule.waitForIdle()
            val burst = composedPageTops(SCROLLED_PAGE_COUNT)

            val diagnostics =
                "root ${width}x$height, tap $point, fit $fitWidth, " +
                    "slow $slow, burst $burst"

            assertWithMessage(diagnostics)
                .that(placedPageLeft(SCROLLED_PAGE_COUNT))
                .isWithin(TOLERANCE_PX)
                .of(slowLeft)

            val common = placedRun(slow).intersect(placedRun(burst).toSet())
            assertWithMessage(diagnostics).that(common).isNotEmpty()
            common.forEach { index ->
                assertWithMessage("page $index; $diagnostics")
                    .that(burst.getValue(index))
                    .isWithin(height * TOP_TOLERANCE_FRACTION)
                    .of(slow.getValue(index))
            }
        }
    }

    @Test
    fun zoomScalesTheGapsBetweenPages() {
        // Strip-shaped pages, so several page boundaries sit under one screen.
        // The distance between two pages is their heights plus the gaps between
        // them; if the gaps do not scale with the zoom, that distance grows by
        // less than the zoom, and an anchor spanning those boundaries lands
        // short by exactly the difference.
        withReader(pageCount = STRIP_PAGE_COUNT, pageSize = STRIP_PAGE) {
            val before = composedPageTops(STRIP_PAGE_COUNT)

            val zoom = doubleTapAt(x = 0.5f)

            val after = composedPageTops(STRIP_PAGE_COUNT)
            // Compare page pitch — one page plus one gap — rather than the span
            // between two named pages: the zoom scrolls, so the two screenfuls
            // need not share any page, and this needs no assumption about which
            // pages they are or where the reader sits in the window.
            val runBefore = placedRun(before)
            val runAfter = placedRun(after)
            val diagnostics = "before $before (run $runBefore), after $after (run $runAfter)"
            assertWithMessage(diagnostics).that(runBefore.size).isAtLeast(MIN_RUN)
            assertWithMessage(diagnostics).that(runAfter.size).isAtLeast(MIN_RUN)

            assertWithMessage("zoom $zoom, $diagnostics")
                .that(pagePitch(after, runAfter))
                .isWithin(GAP_TOLERANCE_PX)
                .of(pagePitch(before, runBefore) * zoom)
        }
    }

    @Test
    fun pinch_zoomsAboutTheMidpointOfTheFingers() {
        // A pinch turns the document about the point between the fingers. The
        // gesture begins on the event that puts the second finger down, and on
        // that event Compose's centroid helper — which only averages pointers
        // that were down in the previous event too — reports the *first*
        // finger's position, half a spread away from where the user pinched.
        // Fingers on one vertical line, so only the vertical pivot can explain
        // where the page ends up.
        withReader(pageCount = 3) {
            val (width, height) = viewportSize()
            val focusY = height / 2f
            val spread = height * PINCH_SPREAD_FRACTION
            val topBefore = pageTop()
            val fitWidth = pageWidth()

            composeRule.onRoot().performTouchInput {
                pinch(
                    start0 = Offset(width / 2f, focusY - spread),
                    end0 = Offset(width / 2f, focusY - spread * PINCH_FACTOR),
                    start1 = Offset(width / 2f, focusY + spread),
                    end1 = Offset(width / 2f, focusY + spread * PINCH_FACTOR),
                    durationMillis = SWIPE_MS,
                )
            }
            composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS) {
                pageWidth() > fitWidth * ZOOM_IN_THRESHOLD
            }
            composeRule.waitForIdle()

            val zoom = pageWidth().toFloat() / fitWidth
            assertWithMessage(
                "root ${width}x$height, focusY $focusY, spread $spread, zoom $zoom, " +
                    "page 1 top $topBefore -> ${pageTop()}",
            ).that(pageTop())
                .isWithin(height * TOP_TOLERANCE_FRACTION)
                .of(focusY + (topBefore - focusY) * zoom)
        }
    }

    @Test
    fun oneFingerDrag_pansAndScrollsInTheSameGesture() {
        withReader(pageCount = 3) {
            val (width, height) = viewportSize()
            doubleTapAt(x = 0.5f)

            val leftBefore = pageLeft()
            val topBefore = pageTop()

            // One diagonal drag up and to the left: the document should follow
            // in both axes at once.
            composeRule.onRoot().performTouchInput {
                swipe(
                    start = Offset(width * 0.7f, height * 0.7f),
                    end = Offset(width * 0.4f, height * 0.4f),
                    durationMillis = SWIPE_MS,
                )
            }
            composeRule.waitForIdle()

            assertThat(pageLeft()).isLessThan(leftBefore - TOLERANCE_PX)
            assertThat(pageTop()).isLessThan(topBefore - TOLERANCE_PX)
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
        const val ZOOM_TIMEOUT_MS = 5_000L
        const val SWIPE_MS = 300L

        // Strip pages, so however the zoom-in leaves the reader positioned it
        // is at most one short page into that page — which is what makes the
        // zoom-out that follows a reliably *backward* anchor. Starting halfway
        // through the document leaves several screenfuls of travel either way,
        // so neither end of the document can clamp the anchor.
        const val SCROLLED_PAGE_COUNT = 30
        const val START_PAGE = 15

        // Well clear of the chrome at either end of the screen, and far enough
        // apart that the anchor between them is a big backward scroll: zooming
        // in about the upper line and back out about the lower one puts the
        // anchor a third of a viewport or more above the page the reader sat
        // on — many times TOP_TOLERANCE_FRACTION.
        const val ZOOM_IN_Y = 0.3f
        const val ZOOM_OUT_Y = 0.7f

        // Where the burst lands. Off-centre horizontally, so holding that point
        // still at 2.5x pans the document a good fraction of a viewport and the
        // round trip has a real distance to come back from. Below the middle
        // vertically, so the zoom-out's anchor travels back *above* the page the
        // zoom-in left the reader on rather than clamping at its top.
        const val TAP_X = 0.7f
        const val BURST_Y = 0.6f

        // Pages and gaps round to whole pixels and the reader's inset is not
        // modelled, so the landing lands within a small fraction of a screen —
        // far tighter than the gap between centring and pinning, which at
        // TAP_X is a fifth of a viewport.
        const val LANDING_TOLERANCE_FRACTION = 0.03f

        // Enough short pages that a zoom has somewhere to travel and at least
        // two survive on screen either side of it.
        const val ROLLOVER_PAGE_COUNT = 8

        // Fingers start this far either side of the point being pinched about,
        // and end PINCH_FACTOR times as far apart — so the document doubles,
        // both fingers stay on screen, and pivoting on the first finger instead
        // of the midpoint misses by a spread's worth of magnification: many
        // times TOP_TOLERANCE_FRACTION either way.
        const val PINCH_SPREAD_FRACTION = 0.18f
        const val PINCH_FACTOR = 2f

        // The reading zoom is 2.5x; require clearly-more-than-fit so a stray
        // single tap (no zoom) can never satisfy the wait.
        const val ZOOM_IN_THRESHOLD = 1.5f

        // Sub-pixel rounding drift in the committed offsets.
        const val TOLERANCE_PX = 2f

        // Pages an eighth as tall as they are wide, so a screenful spans several
        // page boundaries; enough of them that a zoomed document still has room
        // to scroll to its anchor.
        val STRIP_PAGE = PDRectangle(600f, 75f)
        const val STRIP_PAGE_COUNT = 12

        // Pages and gaps round to whole pixels, so a pitch drifts a little
        // either way. An unscaled gap costs the pitch 12px at the 2.5x reading
        // zoom, so this absorbs the rounding without hiding that.
        const val GAP_TOLERANCE_PX = 4f

        // Long enough that the handful of pages the reuse pool can hold never
        // outnumber the ones actually on screen.
        const val MIN_RUN = 3

        // A page 0.7 as tall as it is wide. Tapping the middle of the screen
        // then lands an anchor offset of 0.75 x viewport height, between the
        // page's fit height (0.7 x viewport *width*) and its zoomed height —
        // the rollover case — for any screen from square to 2.3:1.
        val SHORT_PAGE = PDRectangle(600f, 420f)

        // The committed offset is a rounded pixel value and the un-scaled gap
        // between pages drifts a little; a mis-anchored zoom misses by pages.
        const val TOP_TOLERANCE_FRACTION = 0.03f
    }
}
