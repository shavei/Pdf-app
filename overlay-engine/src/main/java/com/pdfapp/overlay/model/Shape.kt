package com.pdfapp.overlay.model

import com.pdfapp.core.renderer.model.PdfPoint
import java.util.UUID

/** The vector shapes the user can draw over a page. */
enum class ShapeKind { RECTANGLE, ELLIPSE, LINE, ARROW }

/**
 * A vector shape drawn by dragging from [start] to [end], both in PDF user-space
 * points (origin bottom-left). Storing the two anchors in points — not pixels —
 * keeps the shape resolution-independent so it flattens cleanly at any page scale,
 * exactly like [InkSignature].
 *
 * For [ShapeKind.RECTANGLE] and [ShapeKind.ELLIPSE] the anchors are opposite
 * corners of the bounding box (order-independent); for [ShapeKind.LINE] and
 * [ShapeKind.ARROW] they are the two endpoints, with the arrowhead drawn at [end].
 *
 * @param strokeWidthPt pen width in PDF points.
 * @param strokeColorArgb packed ARGB stroke colour.
 */
data class Shape(
    val kind: ShapeKind,
    val start: PdfPoint,
    val end: PdfPoint,
    val strokeWidthPt: Float = DEFAULT_STROKE_WIDTH_PT,
    val strokeColorArgb: Int = DEFAULT_COLOR,
    val id: String = UUID.randomUUID().toString(),
) {
    /** True when the shape has collapsed to a point and there is nothing to draw. */
    val isEmpty: Boolean get() = start.x == end.x && start.y == end.y

    companion object {
        const val DEFAULT_STROKE_WIDTH_PT = 2f
        const val DEFAULT_COLOR = 0xFFB00020.toInt()
    }
}
