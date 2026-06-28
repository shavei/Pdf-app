package com.pdfapp.overlay.model

import com.pdfapp.core.renderer.model.PdfPoint
import java.util.UUID

/**
 * A single text overlay placed on a page.
 *
 * @param position baseline-left anchor in PDF user-space points.
 * @param fontSizePt font size in PDF points.
 * @param colorArgb packed ARGB colour.
 */
data class TextOverlay(
    val text: String,
    val position: PdfPoint,
    val fontSizePt: Float = DEFAULT_FONT_SIZE_PT,
    val colorArgb: Int = DEFAULT_COLOR,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {
        const val DEFAULT_FONT_SIZE_PT = 14f
        const val DEFAULT_COLOR = 0xFF000000.toInt()
    }
}
