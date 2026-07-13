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
 * Rendering surface of a document: bitmaps plus page dimensions. Implemented
 * by [PageRenderer]; an interface so caches/UI can be tested with fakes.
 *
 * Implementations are NOT safe for concurrent calls — [android.graphics.pdf.PdfRenderer]
 * permits one open page at a time. Serialize access (see `RenderedPageCache`).
 */
interface PageRendering {
    /** Render page [index] at [pixelsPerPoint]. */
    suspend fun renderPage(
        index: Int,
        pixelsPerPoint: Float,
    ): RenderedPage

    /** Displayed size of page [index] in PDF points, without rasterising it. */
    suspend fun pageSize(index: Int): PageSize
}

/**
 * Renders pages of a [PdfDocumentSource] to bitmaps off the main thread.
 *
 * @param ioDispatcher injectable so rendering work can be driven deterministically in tests.
 */
class PageRenderer(
    private val source: PdfDocumentSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageRendering {
    /**
     * Render [index] at [pixelsPerPoint] (e.g. 2.0 ≈ 144 dpi). The returned
     * [RenderedPage.mapper] converts between the produced bitmap and PDF points.
     */
    override suspend fun renderPage(
        index: Int,
        pixelsPerPoint: Float,
    ): RenderedPage =
        withContext(ioDispatcher) {
            source.openPage(index).use { page ->
                val pageSize = PageSize(page.width.toFloat(), page.height.toFloat())
                val mapper = CoordinateMapper(pageSize, pixelsPerPoint)

                val bitmap =
                    Bitmap
                        .createBitmap(
                            mapper.bitmapWidthPx.toInt().coerceAtLeast(1),
                            mapper.bitmapHeightPx.toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        ).apply { eraseColor(Color.WHITE) }

                val transform = Matrix().apply { setScale(pixelsPerPoint, pixelsPerPoint) }
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                RenderedPage(index, bitmap, pageSize, mapper)
            }
        }

    override suspend fun pageSize(index: Int): PageSize =
        withContext(ioDispatcher) {
            source.openPage(index).use { page ->
                PageSize(page.width.toFloat(), page.height.toFloat())
            }
        }
}
