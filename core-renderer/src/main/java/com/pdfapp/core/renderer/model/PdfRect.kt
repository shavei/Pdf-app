package com.pdfapp.core.renderer.model

/**
 * An axis-aligned rectangle in PDF user space: origin bottom-left, y-axis
 * pointing up, units in points. Used for glyph boxes, search-match quads and
 * link hit-areas so geometry stays zoom-independent (see [PdfPoint]).
 */
data class PdfRect(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom

    /** Centre of the rectangle, for nearest-glyph lookups. */
    val center: PdfPoint get() = PdfPoint((left + right) / 2f, (bottom + top) / 2f)

    fun contains(point: PdfPoint): Boolean = point.x in left..right && point.y in bottom..top

    /** Smallest rectangle covering this and [other]. */
    fun union(other: PdfRect): PdfRect =
        PdfRect(
            left = minOf(left, other.left),
            bottom = minOf(bottom, other.bottom),
            right = maxOf(right, other.right),
            top = maxOf(top, other.top),
        )
}
