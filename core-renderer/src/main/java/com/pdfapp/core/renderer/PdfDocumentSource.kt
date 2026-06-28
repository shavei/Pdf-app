package com.pdfapp.core.renderer

import android.content.ContentResolver
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable

/**
 * Owns the open [PdfRenderer] and the file descriptor backing it for a single
 * document opened from a SAF [Uri].
 *
 * Note: [PdfRenderer] is *read-only*. It can render pages but cannot write or
 * modify a PDF — saving is handled by the `:file-persistence` module. See
 * CLAUDE.md "Never Do".
 */
class PdfDocumentSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    /** Number of pages in the document. */
    val pageCount: Int get() = renderer.pageCount

    /**
     * Open a page for rendering. Callers MUST close the returned page before
     * opening another — [PdfRenderer] only permits one open page at a time.
     */
    fun openPage(index: Int): PdfRenderer.Page {
        require(index in 0 until pageCount) { "Page $index out of bounds (0..${pageCount - 1})" }
        return renderer.openPage(index)
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        /** Open a document from a SAF [Uri] for read access. */
        fun fromUri(resolver: ContentResolver, uri: Uri): PdfDocumentSource {
            val pfd = resolver.openFileDescriptor(uri, "r")
                ?: error("Unable to open file descriptor for $uri")
            return PdfDocumentSource(pfd, PdfRenderer(pfd))
        }
    }
}
