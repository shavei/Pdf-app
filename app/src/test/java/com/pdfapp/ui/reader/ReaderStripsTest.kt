package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which sharp strips a zoomed page renders. The stability cases are the point:
 * the window has to stay *equal* across a frame's worth of scrolling, because
 * that equality is what stops a `derivedStateOf` over it recomposing every
 * visible page on every frame of a gesture.
 */
class ReaderStripsTest {
    // A page split four ways, each strip exactly one viewport tall.
    private fun range(topPx: Int) =
        ReaderStrips.visibleRange(
            topPx = topPx,
            pageHeightPx = PAGE,
            viewportHeightPx = VIEWPORT,
            bucket = BUCKET,
        )

    @Test
    fun pageAtTheTopRendersTheStripsOnScreenPlusTheMargin() {
        // Strip 0 fills the viewport; the half-strip margin reaches into 1.
        assertThat(range(topPx = 0)).isEqualTo(0..1)
    }

    @Test
    fun scrollingDownWalksTheWindowDownThePage() {
        assertThat(range(topPx = -2000)).isEqualTo(1..3)
        assertThat(range(topPx = -3000)).isEqualTo(2..3)
    }

    @Test
    fun windowStopsAtThePageEnds() {
        // Never below strip 0 above the page, nor past the last strip below it.
        assertThat(range(topPx = 500).first).isAtLeast(0)
        assertThat(range(topPx = -3000).last).isAtMost(BUCKET - 1)
    }

    @Test
    fun pageScrolledPastRendersNothing() {
        assertThat(range(topPx = -20_000).isEmpty()).isTrue()
    }

    @Test
    fun pageNotYetReachedRendersNothing() {
        assertThat(range(topPx = 20_000).isEmpty()).isTrue()
    }

    @Test
    fun unzoomedPageRendersNoStrips() {
        // At bucket 1 the base bitmap is already sharp.
        assertThat(
            ReaderStrips
                .visibleRange(topPx = 0, pageHeightPx = PAGE, viewportHeightPx = VIEWPORT, bucket = 1)
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun degeneratePageRendersNothing() {
        assertThat(
            ReaderStrips
                .visibleRange(topPx = 0, pageHeightPx = 0f, viewportHeightPx = VIEWPORT, bucket = BUCKET)
                .isEmpty(),
        ).isTrue()
    }

    @Test
    fun aFramesWorthOfScrollingKeepsTheSameWindow() {
        // ~10px between frames at a brisk fling, nowhere near a 1000px strip:
        // the window must compare equal so nothing downstream is invalidated.
        val settled = range(topPx = -2000)
        assertThat(range(topPx = -2010)).isEqualTo(settled)
        assertThat(range(topPx = -2100)).isEqualTo(settled)
        assertThat(range(topPx = -1900)).isEqualTo(settled)
    }

    @Test
    fun crossingAStripBoundaryMovesTheWindowOnce() {
        // It does change eventually — otherwise nothing new would be rendered.
        assertThat(range(topPx = -2600)).isNotEqualTo(range(topPx = -2000))
    }

    private companion object {
        const val PAGE = 4000f
        const val VIEWPORT = 1000f
        const val BUCKET = 4
    }
}
