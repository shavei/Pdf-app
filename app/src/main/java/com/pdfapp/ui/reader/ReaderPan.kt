package com.pdfapp.ui.reader

import kotlin.math.max

/**
 * Horizontal pan math for the continuous reader, kept Compose-free so it is
 * unit-testable on the JVM like [ReaderZoom] and [ReaderPrefetch].
 *
 * The reader owns its horizontal offset rather than delegating it to a
 * `horizontalScroll` container, for two reasons a scroll container cannot fix:
 *
 * 1. A scroll container claims a drag for its own orientation, so a one-finger
 *    drag over the nested vertical list and horizontal scroll could only ever
 *    move along one axis — panning a zoomed page in straight lines instead of
 *    following the finger.
 * 2. It clamps to a `maxValue` refreshed only when it re-measures. Committing a
 *    zoom and its horizontal anchor in the same frame therefore clamped the
 *    anchor against the *pre-zoom* width — zero at fit-width — so zooming in
 *    always snapped back to the left edge instead of staying under the finger.
 *
 * [pan] is the content-space x of the viewport's left edge, in view pixels: 0
 * shows the content's left edge, [maxPan] its right edge.
 */
object ReaderPan {
    /** How far the content can pan before its right edge reaches the viewport. */
    fun maxPan(
        contentWidthPx: Float,
        viewportWidthPx: Float,
    ): Float = max(0f, contentWidthPx - viewportWidthPx)

    /** [pan] confined to the pannable range; content no wider than the view pins to 0. */
    fun clamp(
        pan: Float,
        contentWidthPx: Float,
        viewportWidthPx: Float,
    ): Float = pan.coerceIn(0f, maxPan(contentWidthPx, viewportWidthPx))

    /**
     * The pan that keeps whatever sits under the view-space x [focusX] still
     * under it after the content scales by [scaleFactor], less the [gesturePan]
     * a live gesture already showed (view-space, fingers-down positive).
     *
     * Unclamped: the caller [clamp]s it against the width the document is
     * zooming *to*, which is the whole point of owning the offset — the anchor
     * is correct the moment it is written, with no relayout in between.
     */
    fun anchored(
        pan: Float,
        focusX: Float,
        scaleFactor: Float,
        gesturePan: Float,
    ): Float = (pan + focusX) * scaleFactor - focusX - gesturePan
}
