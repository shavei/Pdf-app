package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Landing a search hit on screen rather than just the page that holds it. */
class ReaderFocusTest {
    private val viewportHeight = 2_000f
    private val viewportWidth = 1_000f

    @Test
    fun `a hit deep in a page is scrolled to, not left below the fold`() {
        // A page three viewports tall, hit two thirds of the way down: jumping
        // by page index alone would leave it 4000px off screen.
        val offset = ReaderFocus.scrollOffsetPx(focusTopPx = 4_000f, viewportHeightPx = viewportHeight)

        assertThat(offset).isEqualTo(3_400)
    }

    @Test
    fun `the hit lands below the top edge, with the line above it visible`() {
        val offset = ReaderFocus.scrollOffsetPx(focusTopPx = 1_500f, viewportHeightPx = viewportHeight)

        // 1500 - 30% of the viewport: the hit sits 600px down rather than flush
        // against the chrome.
        assertThat(offset).isEqualTo(900)
        assertThat(1_500 - offset).isEqualTo(600)
    }

    @Test
    fun `a hit in the first lines leaves the page top where it is`() {
        // Negative offsets do not exist — the list measures an offset *into* the
        // item — and the page top is already the right place to stop.
        assertThat(ReaderFocus.scrollOffsetPx(0f, viewportHeight)).isEqualTo(0)
        assertThat(ReaderFocus.scrollOffsetPx(400f, viewportHeight)).isEqualTo(0)
    }

    @Test
    fun `a hit already on screen does not move a zoomed reader sideways`() {
        val pan =
            ReaderFocus.panFor(
                pan = 500f,
                focusLeftPx = 700f,
                focusRightPx = 900f,
                viewportWidthPx = viewportWidth,
            )

        assertThat(pan).isNull()
    }

    @Test
    fun `a hit off to the right is panned into view`() {
        val pan =
            ReaderFocus.panFor(
                pan = 0f,
                focusLeftPx = 1_800f,
                focusRightPx = 2_000f,
                viewportWidthPx = viewportWidth,
            )

        // Placed a quarter of the viewport in, so the text before it is visible.
        assertThat(pan).isEqualTo(1_550f)
    }

    @Test
    fun `a hit off to the left is panned back`() {
        val pan =
            ReaderFocus.panFor(
                pan = 2_000f,
                focusLeftPx = 300f,
                focusRightPx = 500f,
                viewportWidthPx = viewportWidth,
            )

        assertThat(pan).isEqualTo(50f)
    }

    @Test
    fun `a hit wider than the viewport is aligned to where reading it starts`() {
        val pan =
            ReaderFocus.panFor(
                pan = 0f,
                focusLeftPx = 1_200f,
                focusRightPx = 3_000f,
                viewportWidthPx = viewportWidth,
            )

        // Its end cannot fit, so the pan shows its beginning rather than
        // chasing an edge that will never be on screen with the other.
        assertThat(pan).isEqualTo(950f)
    }
}
