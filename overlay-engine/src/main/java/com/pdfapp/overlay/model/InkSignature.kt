package com.pdfapp.overlay.model

import com.pdfapp.core.renderer.model.PdfPoint
import java.util.UUID

/**
 * A hand-drawn ink signature: one or more strokes, each a polyline of PDF
 * user-space points. Storing strokes in PDF points (not pixels) keeps the
 * signature resolution-independent so it flattens cleanly at any page scale.
 *
 * @param strokes ordered strokes; each stroke is an ordered list of points.
 * @param strokeWidthPt pen width in PDF points.
 * @param colorArgb packed ARGB colour.
 */
data class InkSignature(
    val strokes: List<List<PdfPoint>>,
    val strokeWidthPt: Float = DEFAULT_STROKE_WIDTH_PT,
    val colorArgb: Int = DEFAULT_COLOR,
    val id: String = UUID.randomUUID().toString(),
) {
    /** True when there is nothing to draw (no strokes, or only empty strokes). */
    val isEmpty: Boolean get() = strokes.all { it.isEmpty() }

    companion object {
        const val DEFAULT_STROKE_WIDTH_PT = 2f
        const val DEFAULT_COLOR = 0xFF001A66.toInt()
    }
}
