package com.pdfapp.ui.reader

/**
 * Pure focal-anchoring math for the continuous reader's pinch-zoom and
 * two-finger pan. Kept free of Compose/Android types so the "content under the
 * fingers stays under the fingers" guarantee can be unit-tested on the JVM.
 *
 * Both axes are expressed in scroll-offset pixels of the current layout: the
 * horizontal axis is a plain scroll container, the vertical axis a lazy list
 * whose total height scales with zoom. For a scale change of `zoomRatio` about a
 * viewport focus, plus a two-finger `pan`, the scroll offset must move so the
 * content sample beneath the focus is unchanged.
 */
internal object ReaderZoomMath {
    /**
     * New horizontal scroll offset that keeps the content under [focusX] fixed
     * while the page width scales by [zoomRatio], then pans by [pan] view pixels
     * (fingers moving right pan the content right, reducing the offset).
     */
    fun horizontalOffset(
        scroll: Float,
        focusX: Float,
        zoomRatio: Float,
        pan: Float,
    ): Float = (scroll + focusX) * zoomRatio - focusX - pan

    /**
     * Vertical scroll delta (positive scrolls toward later pages) that keeps the
     * content under [focusY] fixed as the list height scales by [zoomRatio],
     * then pans by [pan] view pixels (fingers moving down pan the content down).
     */
    fun verticalDelta(
        scroll: Float,
        focusY: Float,
        zoomRatio: Float,
        pan: Float,
    ): Float = (scroll + focusY) * (zoomRatio - 1f) - pan
}
