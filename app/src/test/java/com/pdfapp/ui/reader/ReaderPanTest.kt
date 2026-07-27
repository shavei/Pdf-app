package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Horizontal pan math for the reader. The anchoring cases are the regression
 * guard for zooming about a point: a scroll container clamped the new offset
 * against its pre-zoom width — zero at fit-width — so every zoom-in snapped
 * back to the left edge instead of staying under the finger.
 */
class ReaderPanTest {
    @Test
    fun contentNarrowerThanView_cannotPan() {
        assertThat(ReaderPan.maxPan(contentWidthPx = 800f, viewportWidthPx = 1000f)).isEqualTo(0f)
        assertThat(ReaderPan.clamp(pan = 300f, contentWidthPx = 800f, viewportWidthPx = 1000f))
            .isEqualTo(0f)
    }

    @Test
    fun panStopsAtTheContentEdges() {
        // 2.5x a 1000px viewport leaves 1500px of travel.
        assertThat(ReaderPan.maxPan(contentWidthPx = 2500f, viewportWidthPx = 1000f)).isEqualTo(1500f)
        assertThat(ReaderPan.clamp(pan = -200f, contentWidthPx = 2500f, viewportWidthPx = 1000f))
            .isEqualTo(0f)
        assertThat(ReaderPan.clamp(pan = 9000f, contentWidthPx = 2500f, viewportWidthPx = 1000f))
            .isEqualTo(1500f)
        assertThat(ReaderPan.clamp(pan = 600f, contentWidthPx = 2500f, viewportWidthPx = 1000f))
            .isEqualTo(600f)
    }

    @Test
    fun zoomKeepsTheFocusedPointUnderTheFinger() {
        // Double-tap at x=800 of a 1000px viewport, fit-width → 2.5x. The point
        // 800px into the content lands at 2000px once scaled, so the viewport's
        // left edge has to sit at 2000 - 800 = 1200 for it to stay put.
        val pan =
            ReaderPan.anchored(
                pan = 0f,
                focus = 800f,
                scaleFactor = 2.5f,
                gesturePan = 0f,
            )
        assertThat(pan).isEqualTo(1200f)
    }

    @Test
    fun zoomAnchorsFromAnAlreadyPannedPosition() {
        // Already panned 200px in, pinching 2x about x=300: the content point
        // 500px in doubles to 1000px, which sits at x=300 when the left edge is
        // at 700.
        val pan =
            ReaderPan.anchored(
                pan = 200f,
                focus = 300f,
                scaleFactor = 2f,
                gesturePan = 0f,
            )
        assertThat(pan).isEqualTo(700f)
    }

    @Test
    fun anchorClampsAgainstTheZoomedWidth_notThePreviousOne() {
        // The regression, as the reader composes the two calls: at fit-width
        // there is nowhere to pan, so clamping against the *old* 1000px width
        // forced every anchor to 0. Against the post-zoom width it survives.
        val anchored =
            ReaderPan.anchored(
                pan = 0f,
                focus = 900f,
                scaleFactor = 2.5f,
                gesturePan = 0f,
            )
        assertThat(ReaderPan.clamp(anchored, contentWidthPx = 1000f, viewportWidthPx = 1000f))
            .isEqualTo(0f)
        assertThat(ReaderPan.clamp(anchored, contentWidthPx = 2500f, viewportWidthPx = 1000f))
            .isEqualTo(1350f)
    }

    @Test
    fun anchorNeverExposesContentPastTheRightEdge() {
        // Zooming about the right edge while the pinch also dragged the fingers
        // left asks for more pan than the content has; it stops at the edge.
        val anchored =
            ReaderPan.anchored(
                pan = 0f,
                focus = 1000f,
                scaleFactor = 2.5f,
                gesturePan = -300f,
            )
        assertThat(anchored).isEqualTo(1800f)
        assertThat(ReaderPan.clamp(anchored, contentWidthPx = 2500f, viewportWidthPx = 1000f))
            .isEqualTo(1500f)
    }

    @Test
    fun livePinchPanIsFoldedIn() {
        // A pinch that also dragged the fingers 200px right shows content 200px
        // further left, and committing must keep exactly that.
        val pan =
            ReaderPan.anchored(
                pan = 800f,
                focus = 500f,
                scaleFactor = 1f,
                gesturePan = 200f,
            )
        assertThat(pan).isEqualTo(600f)
    }

    @Test
    fun anchorDeltaIsTheTravelFromWhereTheViewportSits() {
        // The same anchor, expressed as a distance: 1200 away from an offset
        // of 0 is 1200 of travel.
        assertThat(ReaderPan.anchorDelta(pan = 0f, focus = 800f, scaleFactor = 2.5f, gesturePan = 0f))
            .isEqualTo(1200f)
        // From 200 in, the 700 anchor is only 500 of travel.
        assertThat(ReaderPan.anchorDelta(pan = 200f, focus = 300f, scaleFactor = 2f, gesturePan = 0f))
            .isEqualTo(500f)
    }

    @Test
    fun zoomingOutTravelsBackwards() {
        // The regression the vertical axis hit: 400px into the first visible
        // page at 2.5x, double-tapping back to fit-width at y=1000. The line
        // under the finger belongs 440px *above* that page's top, so the
        // travel is negative — and a scroll offset, which cannot go below the
        // page it is measured from, could only ever pin it to zero and drop
        // the reader 840px away from where it was asked to stay.
        assertThat(ReaderPan.anchored(pan = 400f, focus = 1000f, scaleFactor = 0.4f, gesturePan = 0f))
            .isWithin(ROUNDING).of(-440f)
        assertThat(ReaderPan.anchorDelta(pan = 400f, focus = 1000f, scaleFactor = 0.4f, gesturePan = 0f))
            .isWithin(ROUNDING).of(-840f)
    }

    @Test
    fun zoomingInCanAlsoTravelBackwards() {
        // Not only zoom-outs: a pinch that dragged the fingers 900px down the
        // screen while magnifying a little still reveals content above.
        assertThat(ReaderPan.anchorDelta(pan = 100f, focus = 500f, scaleFactor = 1.2f, gesturePan = 900f))
            .isWithin(ROUNDING).of(-780f)
    }

    @Test
    fun zoomingBackToFitWidthResetsThePan() {
        // Fit-width has no travel, so whatever the pan was collapses to 0 —
        // which is also what keeps a zoom-out centred.
        val anchored =
            ReaderPan.anchored(
                pan = 1200f,
                focus = 500f,
                scaleFactor = 1f / 2.5f,
                gesturePan = 0f,
            )
        assertThat(ReaderPan.clamp(anchored, contentWidthPx = 1000f, viewportWidthPx = 1000f))
            .isEqualTo(0f)
    }

    private companion object {
        // Fit-width is 1/2.5 of the reading zoom, which no float holds exactly.
        const val ROUNDING = 0.01f
    }
}
