package com.pdfapp.ui.reader

import kotlin.math.roundToInt

/**
 * Where the reader has to put itself to show a specific box on a page — a search
 * hit — rather than merely the page holding it. Compose-free like [ReaderPan]
 * and [ReaderZoom], so the arithmetic is unit-testable on the JVM.
 *
 * Jumping by page index alone puts the page *top* at the viewport top, which is
 * fine for the outline and thumbnails and wrong for search: a hit in the last
 * line of a page, or off to the side of a zoomed one, is then navigated "to"
 * without ever being on screen (backlog M13).
 *
 * All px values are view pixels at the current zoom. Horizontal ones are in the
 * same content space as [ReaderPan]'s offset: the page's own left edge plus the
 * centering the list applies when the content is narrower than the viewport.
 */
object ReaderFocus {
    /**
     * How far down the viewport a focused hit lands. Not the top edge: a match
     * flush against it reads as though the scroll stopped short, and the line
     * above is usually the sentence the match belongs to. Not the middle
     * either — that wastes the screen above it on a hit near the page foot,
     * where the list cannot scroll far enough anyway.
     */
    const val VERTICAL_LANDING = 0.3f

    /** The same idea horizontally: a little of the line before the hit stays visible. */
    const val HORIZONTAL_LANDING = 0.25f

    /**
     * The scroll offset — px below the page's top — that lands a hit whose top
     * edge sits [focusTopPx] into the page at [VERTICAL_LANDING] of the
     * viewport. Never negative: the list expresses an offset as a distance
     * *into* the item, and a hit in the first lines simply leaves the page top
     * where it is.
     */
    fun scrollOffsetPx(
        focusTopPx: Float,
        viewportHeightPx: Float,
    ): Int = (focusTopPx - viewportHeightPx * VERTICAL_LANDING).coerceAtLeast(0f).roundToInt()

    /**
     * The horizontal pan that brings a hit spanning [focusLeftPx]..[focusRightPx]
     * into view, or null when it is already there — a zoomed reader stays where
     * the user panned it unless the match genuinely is off screen.
     *
     * The result is unclamped, like [ReaderPan.anchored]: the caller clamps it
     * against the width the content actually has. A hit wider than the viewport
     * is aligned to its start, which is where reading it begins.
     */
    fun panFor(
        pan: Float,
        focusLeftPx: Float,
        focusRightPx: Float,
        viewportWidthPx: Float,
    ): Float? {
        val visible = focusLeftPx >= pan && focusRightPx <= pan + viewportWidthPx
        return if (visible) null else focusLeftPx - viewportWidthPx * HORIZONTAL_LANDING
    }
}
