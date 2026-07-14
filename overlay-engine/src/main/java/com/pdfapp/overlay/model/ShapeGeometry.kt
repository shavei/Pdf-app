package com.pdfapp.overlay.model

import com.pdfapp.core.renderer.model.PdfPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure geometry helpers shared by the on-screen preview ([com.pdfapp.overlay.OverlayCanvasView])
 * and the flatten pipeline (`:file-persistence`), so an arrowhead is computed the
 * same way whether it is painted to a Canvas or written into a PDF content stream.
 *
 * Everything here is dependency-free maths on [PdfPoint]s, unit-tested on the JVM.
 */
object ShapeGeometry {
    /** Arrowhead barb length as a fraction of the shaft length, clamped to [MAX_HEAD_PT]. */
    private const val HEAD_LENGTH_FRACTION = 0.25f

    /** Longest an arrowhead barb may grow, in PDF points, however long the shaft. */
    private const val MAX_HEAD_PT = 24f

    /** Half-angle between the shaft and each barb, in radians (~30°). */
    private const val HEAD_SPREAD_RADIANS = 0.5f

    /**
     * The two barb endpoints of an arrowhead drawn at [end], pointing back from the
     * shaft that runs [start] → [end]. Returns [end] twice for a zero-length shaft
     * (there is no direction to point), which the caller can treat as "no head".
     */
    fun arrowHeadBarbs(
        start: PdfPoint,
        end: PdfPoint,
    ): Pair<PdfPoint, PdfPoint> {
        val shaft = hypot(end.x - start.x, end.y - start.y)
        if (shaft == 0f) return end to end
        val headLength = (shaft * HEAD_LENGTH_FRACTION).coerceAtMost(MAX_HEAD_PT)
        // Base angle points from the tip back along the shaft toward the start.
        val base = atan2(start.y - end.y, start.x - end.x)
        return barb(end, base + HEAD_SPREAD_RADIANS, headLength) to
            barb(end, base - HEAD_SPREAD_RADIANS, headLength)
    }

    private fun barb(
        tip: PdfPoint,
        angle: Float,
        length: Float,
    ): PdfPoint = PdfPoint(tip.x + length * cos(angle), tip.y + length * sin(angle))
}
