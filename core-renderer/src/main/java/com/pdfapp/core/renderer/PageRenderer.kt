package com.pdfapp.core.renderer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import com.pdfapp.core.renderer.model.CoordinateMapper
import com.pdfapp.core.renderer.model.PageSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A rendered page bitmap together with the geometry needed to place overlays. */
data class RenderedPage(
    val index: Int,
    val bitmap: Bitmap,
    val pageSize: PageSize,
    val mapper: CoordinateMapper,
)

/**
 * Renders pages of a [PdfDocumentSource] to bitmaps off the main thread.
 *
 * @param ioDispatcher injectable so rendering work can be driven deterministically in tests.
 */
class PageRenderer(
    private val source: PdfDocumentSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Render [index] at [pixelsPerPoint] (e.g. 2.0 ≈ 144 dpi). The returned
     * [RenderedPage.mapper] converts between the produced bitmap and PDF points.
     */
    suspend fun renderPage(index: Int, pixelsPerPoint: Float): RenderedPage =
        withContext(ioDispatcher) {
            source.openPage(index).use { page ->
                val pageSize = PageSize(page.width.toFloat(), page.height.toFloat())
                val mapper = CoordinateMapper(pageSize, pixelsPerPoint)

                val bitmap = Bitmap.createBitmap(
                    mapper.bitmapWidthPx.toInt().coerceAtLeast(1),
                    mapper.bitmapHeightPx.toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                ).apply { eraseColor(Color.WHITE) }

                val transform = Matrix().apply { setScale(pixelsPerPoint, pixelsPerPoint) }
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                RenderedPage(index, bitmap, pageSize, mapper)
            }
        }
}
