package com.pdfapp.ui

import android.content.ContentResolver
import android.net.Uri
import com.pdfapp.core.renderer.PageRenderer
import com.pdfapp.core.renderer.PdfDocumentSource
import com.pdfapp.core.renderer.RenderedPageCache
import com.pdfapp.core.renderer.text.PdfLink
import com.pdfapp.core.renderer.text.PdfTextDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * Everything owned per open document: the renderer-backed [source] with its
 * page [cache], the lazily loaded PdfBox [textDocument] (search, selection,
 * outline, links), and — for password-protected files — the decrypted cache
 * copy, deleted again on [close].
 */
class DocumentSession(
    val uri: Uri,
    val displayName: String,
    val source: PdfDocumentSource,
    private val openTextDocument: () -> PdfTextDocument,
    private val decryptedFile: File? = null,
) : Closeable {
    val cache = RenderedPageCache(PageRenderer(source))
    val pageCount: Int get() = source.pageCount

    private val textMutex = Mutex()
    private var textDocument: PdfTextDocument? = null
    private val linksByPage = HashMap<Int, List<PdfLink>>()

    /** The PdfBox view of the document, parsed on first use. Call on IO. */
    suspend fun textDocument(): PdfTextDocument =
        textMutex.withLock {
            textDocument ?: openTextDocument().also { textDocument = it }
        }

    /** Link annotations for [pageIndex], parsed once per page. Call on IO. */
    suspend fun links(pageIndex: Int): List<PdfLink> {
        val document = textDocument()
        return textMutex.withLock {
            linksByPage.getOrPut(pageIndex) { document.links(pageIndex) }
        }
    }

    /** A fresh stream of the *renderable* bytes (decrypted copy if one exists). */
    fun openInputStream(resolver: ContentResolver): InputStream =
        decryptedFile?.inputStream()
            ?: resolver.openInputStream(uri)
            ?: error("Unable to open source PDF")

    override fun close() {
        runCatching { textDocument?.close() }
        runCatching { source.close() }
        cache.clear()
        decryptedFile?.delete()
    }
}
