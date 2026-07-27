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
     * The offset that keeps whatever sits under the view-space coordinate
     * [focus] still under it after the content scales by [scaleFactor], less
     * the [gesturePan] a live gesture already showed (view-space, fingers-down
     * positive).
     *
     * Axis-agnostic: the reader anchors a zoom with it horizontally (against
     * [pan]) and vertically (against the list's scroll offset).
     *
     * Unclamped — a caller that has bounds applies them. Horizontally that is
     * [clamp] against the width the document is zooming *to*, which is the
     * whole point of owning the offset: the anchor is correct the moment it is
     * written, with no relayout in between.
     */
    fun anchored(
        pan: Float,
        focus: Float,
        scaleFactor: Float,
        gesturePan: Float,
    ): Float = (pan + focus) * scaleFactor - focus - gesturePan

    /**
     * How far the viewport has to move for that anchor to land: [anchored] less
     * the [pan] the viewport already sits at.
     *
     * The vertical axis needs this difference rather than the absolute offset,
     * because vertically `pan` is measured from the top of whichever page
     * happens to be first visible — not from the top of the document. An anchor
     * belonging *above* that page is a negative absolute offset, and the only
     * thing an absolute scroll can do with it is pin it to zero, dropping the
     * document at that page's top instead of where the zoom was asked for.
     * That is not a corner case: the anchor is negative whenever [pan] is less
     * than [focus] x (1 - [scaleFactor]) / [scaleFactor] — one and a half times
     * the focal point when a double-tap drops 2.5x back to fit-width, which is
     * most of the page. As a relative scroll the list walks back through the
     * pages above by itself, so the sign stops mattering.
     */
    fun anchorDelta(
        pan: Float,
        focus: Float,
        scaleFactor: Float,
        gesturePan: Float,
    ): Float = anchored(pan, focus, scaleFactor, gesturePan) - pan
}
