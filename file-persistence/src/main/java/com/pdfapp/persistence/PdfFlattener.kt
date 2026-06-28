package com.pdfapp.persistence

import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Burns an [OverlayLayer] permanently into a PDF page using PdfBox-Android.
 *
 * Overlay coordinates are already in PDF user space (origin bottom-left, points),
 * which is exactly PdfBox's content-stream coordinate system — so positions pass
 * straight through with no conversion. The ink signature is drawn as vector
 * strokes (crisper and smaller than a rasterised image, and deterministic to
 * verify), matching the "vector path" option in the architecture.
 */
class PdfFlattener {

    /**
     * Append [layer]'s overlays to its page in [document]. The document is
     * modified in place; the caller saves it via [PdfSaver]. No-op for an empty
     * layer.
     */
    fun flattenInto(document: PDDocument, layer: OverlayLayer) {
        require(layer.pageIndex in 0 until document.numberOfPages) {
            "pageIndex ${layer.pageIndex} out of bounds (0..${document.numberOfPages - 1})"
        }
        if (layer.isEmpty) return

        val page = document.getPage(layer.pageIndex)
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            /* compress = */ true,
            /* resetContext = */ true,
        ).use { stream ->
            layer.texts.forEach { drawText(stream, it) }
            layer.signatures.forEach { drawSignature(stream, it) }
        }
    }

    private fun drawText(stream: PDPageContentStream, overlay: TextOverlay) {
        val (r, g, b) = overlay.colorArgb.toRgb()
        stream.beginText()
        stream.setFont(PDType1Font.HELVETICA, overlay.fontSizePt)
        stream.setNonStrokingColor(r, g, b)
        stream.newLineAtOffset(overlay.position.x, overlay.position.y)
        stream.showText(overlay.text)
        stream.endText()
    }

    private fun drawSignature(stream: PDPageContentStream, signature: InkSignature) {
        if (signature.isEmpty) return
        val (r, g, b) = signature.colorArgb.toRgb()
        stream.setStrokingColor(r, g, b)
        stream.setLineWidth(signature.strokeWidthPt)
        stream.setLineCapStyle(LINE_CAP_ROUND)
        stream.setLineJoinStyle(LINE_JOIN_ROUND)
        signature.strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val start = stroke.first()
            stream.moveTo(start.x, start.y)
            stroke.drop(1).forEach { stream.lineTo(it.x, it.y) }
            stream.stroke()
        }
    }

    private companion object {
        const val LINE_CAP_ROUND = 1
        const val LINE_JOIN_ROUND = 1
    }
}

/** Decompose a packed ARGB int into PdfBox's 0..1 RGB components. */
private fun Int.toRgb(): Triple<Float, Float, Float> {
    val r = (this shr 16 and 0xFF) / 255f
    val g = (this shr 8 and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return Triple(r, g, b)
}
