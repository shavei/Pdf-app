package com.pdfapp.ui.reader

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Which sharp strips of a zoomed page are worth rendering, given where the page
 * currently sits in the list viewport. Pure and Compose-free so the windowing is
 * unit-testable on the JVM, like [ReaderZoom], [ReaderPan] and [ReaderPrefetch].
 *
 * Answering with a *range* rather than a pixel offset is what keeps a zoomed
 * scroll off the recomposition path. A page's offset changes every frame of a
 * scroll; the strips that offset implies change only when a strip boundary
 * crosses the viewport. Deriving the range means the composable reading it wakes
 * a handful of times per page instead of sixty times a second.
 */
object ReaderStrips {
    /**
     * Extra strip rendered beyond each viewport edge, as a fraction of a strip,
     * so a scroll arrives on a sharp band instead of a stretched one.
     */
    const val MARGIN_STRIPS = 0.5f

    /**
     * Strips of a [bucket]-way split page to render, where [topPx] is the page's
     * top relative to the viewport's (negative once scrolled past). Empty when
     * the page is off-screen, or when the zoom is too low to need strips at all.
     */
    fun visibleRange(
        topPx: Int,
        pageHeightPx: Float,
        viewportHeightPx: Float,
        bucket: Int,
    ): IntRange {
        if (bucket <= 1) return IntRange.EMPTY
        val stripHeightPx = pageHeightPx / bucket
        if (stripHeightPx <= 0f) return IntRange.EMPTY
        val margin = stripHeightPx * MARGIN_STRIPS
        val first = floor((-topPx - margin) / stripHeightPx).toInt().coerceAtLeast(0)
        val last =
            (ceil((viewportHeightPx - topPx + margin) / stripHeightPx).toInt() - 1)
                .coerceAtMost(bucket - 1)
        return if (first > last) IntRange.EMPTY else first..last
    }
}
