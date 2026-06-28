package com.pdfapp.core.renderer.model

/**
 * A point in PDF user space: origin bottom-left, y-axis pointing up, units in
 * points (1/72 inch). Overlay positions are stored in this space so they are
 * independent of the zoom/density a page happens to be rendered at.
 */
data class PdfPoint(
    val x: Float,
    val y: Float,
)

/**
 * A point in on-screen bitmap space: origin top-left, y-axis pointing down,
 * units in pixels of the rendered page bitmap.
 */
data class PixelPoint(
    val x: Float,
    val y: Float,
)

/** Size of a PDF page in points. */
data class PageSize(
    val widthPt: Float,
    val heightPt: Float,
) {
    init {
        require(widthPt > 0f && heightPt > 0f) { "Page size must be positive: $widthPt x $heightPt" }
    }
}

/**
 * Single source of truth for converting between rendered-bitmap pixels and PDF
 * user-space points for one page.
 *
 * The two spaces differ in two ways that this class reconciles:
 *  - origin: bitmap is top-left, PDF is bottom-left (so the y-axis is flipped);
 *  - scale: [pixelsPerPoint] pixels represent one PDF point.
 *
 * Kept dependency-free (no Android types) so it can be exhaustively unit tested
 * on the JVM — it is the most correctness-critical class in the project.
 */
class CoordinateMapper(
    val pageSize: PageSize,
    val pixelsPerPoint: Float,
) {
    init {
        require(pixelsPerPoint > 0f) { "pixelsPerPoint must be positive: $pixelsPerPoint" }
    }

    /** Width of the rendered page bitmap, in pixels. */
    val bitmapWidthPx: Float get() = pageSize.widthPt * pixelsPerPoint

    /** Height of the rendered page bitmap, in pixels. */
    val bitmapHeightPx: Float get() = pageSize.heightPt * pixelsPerPoint

    /** Convert a touch/draw position on the bitmap into a PDF user-space point. */
    fun toPdfPoint(pixel: PixelPoint): PdfPoint =
        PdfPoint(
            x = pixel.x / pixelsPerPoint,
            y = pageSize.heightPt - (pixel.y / pixelsPerPoint),
        )

    /** Convert a stored PDF user-space point back into a bitmap pixel position. */
    fun toPixel(point: PdfPoint): PixelPoint =
        PixelPoint(
            x = point.x * pixelsPerPoint,
            y = (pageSize.heightPt - point.y) * pixelsPerPoint,
        )
}
