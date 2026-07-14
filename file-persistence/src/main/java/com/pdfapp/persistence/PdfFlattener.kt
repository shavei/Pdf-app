package com.pdfapp.persistence

import android.content.Context
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.Shape
import com.pdfapp.overlay.model.ShapeGeometry
import com.pdfapp.overlay.model.ShapeKind
import com.pdfapp.overlay.model.TextOverlay
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.InputStream
import kotlin.math.abs

/**
 * Burns an [OverlayLayer] permanently into a PDF page using PdfBox-Android.
 *
 * Overlay coordinates are already in PDF user space (origin bottom-left, points),
 * which is exactly PdfBox's content-stream coordinate system — so positions pass
 * straight through with no conversion. The ink signature is drawn as vector
 * strokes (crisper and smaller than a rasterised image, and deterministic to
 * verify), matching the "vector path" option in the architecture.
 *
 * Text that fits WinAnsi (Latin-1) is drawn with the built-in Helvetica so no
 * font needs embedding. Anything else — Hebrew, Cyrillic, Greek, … — falls back
 * to the bundled Arimo font (Helvetica-metric-compatible, SIL OFL), embedded as
 * a subset so output files stay small. RTL text is reordered to visual order
 * before drawing, because PDF content streams have no bidi of their own.
 */
class PdfFlattener(private val openUnicodeFont: () -> InputStream) {
    /** Opens the bundled Unicode fallback font from [context]'s assets. */
    constructor(context: Context) : this({ context.assets.open(UNICODE_FONT_ASSET) })

    /** Embedded fallback font, loaded at most once per document. */
    private var cachedUnicodeFont: PDFont? = null
    private var cachedFontDocument: PDDocument? = null

    /**
     * Append [layer]'s overlays to its page in [document]. The document is
     * modified in place; the caller saves it via [PdfSaver]. No-op for an empty
     * layer.
     */
    fun flattenInto(
        document: PDDocument,
        layer: OverlayLayer,
    ) {
        require(layer.pageIndex in 0 until document.numberOfPages) {
            "pageIndex ${layer.pageIndex} out of bounds (0..${document.numberOfPages - 1})"
        }
        if (layer.isEmpty) return

        val page = document.getPage(layer.pageIndex)
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            // compress =
            true,
            // resetContext =
            true,
        ).use { stream ->
            layer.shapes.forEach { drawShape(stream, it) }
            layer.texts.forEach { drawText(document, stream, it) }
            layer.signatures.forEach { drawSignature(stream, it) }
        }
    }

    private fun drawText(
        document: PDDocument,
        stream: PDPageContentStream,
        overlay: TextOverlay,
    ) {
        val text = overlay.text.toVisualOrder()
        val (r, g, b) = overlay.colorArgb.toRgb()
        stream.beginText()
        stream.setFont(fontFor(document, text), overlay.fontSizePt)
        stream.setNonStrokingColor(r, g, b)
        stream.newLineAtOffset(overlay.position.x, overlay.position.y)
        stream.showText(text)
        stream.endText()
    }

    /** Built-in Helvetica when it can encode [text]; the embedded Unicode font otherwise. */
    private fun fontFor(
        document: PDDocument,
        text: String,
    ): PDFont =
        if (canEncode(PDType1Font.HELVETICA, text)) {
            PDType1Font.HELVETICA
        } else {
            unicodeFont(document)
        }

    private fun unicodeFont(document: PDDocument): PDFont {
        cachedUnicodeFont
            ?.takeIf { cachedFontDocument === document }
            ?.let { return it }
        val font =
            openUnicodeFont().use { stream ->
                PDType0Font.load(document, stream, EMBED_SUBSET)
            }
        cachedUnicodeFont = font
        cachedFontDocument = document
        return font
    }

    private fun canEncode(
        font: PDFont,
        text: String,
    ): Boolean = runCatching { font.encode(text) }.isSuccess

    /**
     * Draw a vector [shape] as a stroked path. Anchors are already in PDF points
     * (bottom-left origin), so they pass straight into the content stream. Ellipses
     * are approximated by four cubic Béziers — PdfBox has no oval primitive.
     */
    private fun drawShape(
        stream: PDPageContentStream,
        shape: Shape,
    ) {
        if (shape.isEmpty) return
        val (r, g, b) = shape.strokeColorArgb.toRgb()
        stream.setStrokingColor(r, g, b)
        stream.setLineWidth(shape.strokeWidthPt)
        stream.setLineCapStyle(LINE_CAP_ROUND)
        stream.setLineJoinStyle(LINE_JOIN_ROUND)
        val start = shape.start
        val end = shape.end
        when (shape.kind) {
            ShapeKind.RECTANGLE -> {
                stream.addRect(
                    minOf(start.x, end.x),
                    minOf(start.y, end.y),
                    abs(end.x - start.x),
                    abs(end.y - start.y),
                )
                stream.stroke()
            }
            ShapeKind.ELLIPSE -> drawEllipse(stream, shape)
            ShapeKind.LINE -> {
                stream.moveTo(start.x, start.y)
                stream.lineTo(end.x, end.y)
                stream.stroke()
            }
            ShapeKind.ARROW -> {
                stream.moveTo(start.x, start.y)
                stream.lineTo(end.x, end.y)
                stream.stroke()
                val (barb1, barb2) = ShapeGeometry.arrowHeadBarbs(start, end)
                stream.moveTo(end.x, end.y)
                stream.lineTo(barb1.x, barb1.y)
                stream.stroke()
                stream.moveTo(end.x, end.y)
                stream.lineTo(barb2.x, barb2.y)
                stream.stroke()
            }
        }
    }

    /** Trace [shape]'s bounding ellipse with four cubic Bézier quadrants. */
    private fun drawEllipse(
        stream: PDPageContentStream,
        shape: Shape,
    ) {
        val cx = (shape.start.x + shape.end.x) / 2f
        val cy = (shape.start.y + shape.end.y) / 2f
        val rx = abs(shape.end.x - shape.start.x) / 2f
        val ry = abs(shape.end.y - shape.start.y) / 2f
        val ox = rx * ELLIPSE_KAPPA
        val oy = ry * ELLIPSE_KAPPA
        stream.moveTo(cx + rx, cy)
        stream.curveTo(cx + rx, cy + oy, cx + ox, cy + ry, cx, cy + ry)
        stream.curveTo(cx - ox, cy + ry, cx - rx, cy + oy, cx - rx, cy)
        stream.curveTo(cx - rx, cy - oy, cx - ox, cy - ry, cx, cy - ry)
        stream.curveTo(cx + ox, cy - ry, cx + rx, cy - oy, cx + rx, cy)
        stream.stroke()
    }

    private fun drawSignature(
        stream: PDPageContentStream,
        signature: InkSignature,
    ) {
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
        const val UNICODE_FONT_ASSET = "fonts/Arimo-Regular.ttf"
        const val EMBED_SUBSET = true

        // Control-point offset for approximating a circular quadrant with a cubic
        // Bézier: 4/3 · (√2 − 1). Scaled by each radius for the ellipse.
        const val ELLIPSE_KAPPA = 0.5522848f
    }
}

/** Decompose a packed ARGB int into PdfBox's 0..1 RGB components. */
private fun Int.toRgb(): Triple<Float, Float, Float> {
    val r = (this shr 16 and 0xFF) / 255f
    val g = (this shr 8 and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return Triple(r, g, b)
}
