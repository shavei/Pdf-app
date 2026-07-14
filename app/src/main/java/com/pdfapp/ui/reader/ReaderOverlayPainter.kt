package com.pdfapp.ui.reader

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.OverlayLayer

/**
 * Paints a page's user overlays (placed text, committed ink signatures) in
 * the reader so annotations made in edit mode stay visible while reading.
 * Geometry is stored in PDF points; [pointScale] converts to layout pixels.
 */
internal fun DrawScope.drawOverlayLayer(
    layer: OverlayLayer,
    pointScale: Float,
    pageHeightPt: Float,
) {
    fun toX(point: PdfPoint) = point.x * pointScale

    fun toY(point: PdfPoint) = (pageHeightPt - point.y) * pointScale

    layer.signatures.forEach { signature ->
        signature.strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val path =
                Path().apply {
                    stroke.forEachIndexed { i, point ->
                        if (i == 0) moveTo(toX(point), toY(point)) else lineTo(toX(point), toY(point))
                    }
                }
            drawPath(
                path = path,
                color = Color(signature.colorArgb),
                style =
                    Stroke(
                        width = signature.strokeWidthPt * pointScale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }

    if (layer.texts.isEmpty()) return
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        layer.texts.forEach { overlay ->
            paint.color = overlay.colorArgb
            paint.textSize = overlay.fontSizePt * pointScale
            canvas.nativeCanvas.drawText(
                overlay.text,
                toX(overlay.position),
                toY(overlay.position),
                paint,
            )
        }
    }
}
