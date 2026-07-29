package com.pdfapp.core.renderer.text

import com.pdfapp.core.renderer.form.PdfFormField
import com.pdfapp.core.renderer.form.PdfFormReader
import com.pdfapp.core.renderer.model.PdfRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.Closeable
import java.io.InputStream

/** One entry of the document outline (table of contents). */
data class OutlineEntry(
    val title: String,
    val depth: Int,
    val pageIndex: Int,
)

/** A tappable link annotation on a page. */
data class PdfLink(
    val box: PdfRect,
    val target: Target,
) {
    sealed interface Target {
        data class Page(val pageIndex: Int) : Target

        data class Url(val url: String) : Target
    }
}

/**
 * Read-only PdfBox view of a document for everything [android.graphics.pdf.PdfRenderer]
 * cannot do: text geometry (search/selection), the outline, link annotations and
 * AcroForm fields. Complements — never replaces — the renderer, which stays the
 * only rasterisation path.
 *
 * Page text is extracted lazily and cached per page. All methods do parsing
 * work: call them on [kotlinx.coroutines.Dispatchers.IO]. Thread-safe.
 */
class PdfTextDocument private constructor(
    private val document: PDDocument,
) : Closeable {
    private val extractor = PdfTextExtractor()
    private val formReader = PdfFormReader()

    /**
     * Extracted pages, least-recently-used first and bounded (backlog M14).
     *
     * A [PageTextIndex] holds the page's text plus a box per character — order
     * 40 bytes per character — and a full-document search touches every page
     * exactly once. Cached without a bound, one search over a long book pinned
     * every page of it for the life of the session, on top of the bitmap cache,
     * to serve a scan that never looks back. The bound keeps what a reader
     * actually revisits (the visible page and its neighbours, for selection and
     * TalkBack) and re-extracts anything older, which costs one page parse on
     * the IO thread that asked for it.
     */
    private val pageCache =
        object : LinkedHashMap<Int, PageTextIndex>(CACHE_CAPACITY, CACHE_LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageTextIndex>): Boolean =
                size > MAX_CACHED_PAGES
        }
    private var formFieldCache: List<PdfFormField>? = null
    private val lock = Any()

    val pageCount: Int get() = document.numberOfPages

    /** Extracted text + glyph geometry for [pageIndex], cached. */
    fun pageText(pageIndex: Int): PageTextIndex =
        synchronized(lock) {
            pageCache.getOrPut(pageIndex) { extractor.extract(document, pageIndex) }
        }

    /** Case-insensitive matches of [query] on one page. */
    fun searchPage(
        pageIndex: Int,
        query: String,
    ): List<TextMatch> = pageText(pageIndex).search(query)

    /** Flattened outline, depth-first, entries without a resolvable page skipped. */
    fun outline(): List<OutlineEntry> =
        synchronized(lock) {
            val root = document.documentCatalog.documentOutline ?: return emptyList()
            val entries = mutableListOf<OutlineEntry>()
            collectOutline(root.firstChild, depth = 0, entries)
            entries
        }

    /**
     * Every fillable AcroForm widget in the document (plan Phase 4), empty when
     * there is no form. Parsed once and cached — the reader never mutates the
     * document, so the result cannot go stale under us.
     */
    fun formFields(): List<PdfFormField> =
        synchronized(lock) {
            formFieldCache ?: formReader.fields(document).also { formFieldCache = it }
        }

    /**
     * True when this document's only form is XFA. XFA is explicitly out of scope
     * (plan Phase 4), so the UI says so rather than offering an empty form.
     */
    fun isXfaOnlyForm(): Boolean = synchronized(lock) { formReader.isXfaOnly(document) }

    /** Link annotations on [pageIndex] with resolved targets. */
    fun links(pageIndex: Int): List<PdfLink> =
        synchronized(lock) {
            document
                .getPage(pageIndex)
                .annotations
                .filterIsInstance<PDAnnotationLink>()
                .mapNotNull(::toLink)
        }

    override fun close(): Unit = synchronized(lock) { document.close() }

    private fun collectOutline(
        first: PDOutlineItem?,
        depth: Int,
        into: MutableList<OutlineEntry>,
    ) {
        if (depth >= MAX_OUTLINE_DEPTH) return
        var item = first
        while (item != null) {
            pageIndexOf(item)?.let { page ->
                into += OutlineEntry(item.title.orEmpty(), depth, page)
            }
            collectOutline(item.firstChild, depth + 1, into)
            item = item.nextSibling
        }
    }

    private fun pageIndexOf(item: PDOutlineItem): Int? =
        runCatching {
            val page = item.findDestinationPage(document) ?: return null
            document.pages.indexOf(page).takeIf { it >= 0 }
        }.getOrNull()

    private fun toLink(annotation: PDAnnotationLink): PdfLink? {
        val rect = annotation.rectangle ?: return null
        val box =
            PdfRect(
                left = minOf(rect.lowerLeftX, rect.upperRightX),
                bottom = minOf(rect.lowerLeftY, rect.upperRightY),
                right = maxOf(rect.lowerLeftX, rect.upperRightX),
                top = maxOf(rect.lowerLeftY, rect.upperRightY),
            )
        val target = resolveTarget(annotation) ?: return null
        return PdfLink(box, target)
    }

    private fun resolveTarget(annotation: PDAnnotationLink): PdfLink.Target? =
        runCatching {
            when (val action = annotation.action) {
                is PDActionURI -> action.uri?.let { PdfLink.Target.Url(it) }
                is PDActionGoTo -> pageTarget(action.destination as? PDPageDestination)
                else -> pageTarget(annotation.destination as? PDPageDestination)
            }
        }.getOrNull()

    private fun pageTarget(destination: PDPageDestination?): PdfLink.Target.Page? {
        val dest = destination ?: return null
        val index =
            dest.page?.let { page -> document.pages.indexOf(page).takeIf { it >= 0 } }
                ?: dest.pageNumber.takeIf { it >= 0 }
                ?: return null
        return PdfLink.Target.Page(index)
    }

    companion object {
        private const val MAX_OUTLINE_DEPTH = 8

        /**
         * How many extracted pages are kept. Comfortably more than the reader
         * has on screen at any zoom, and small enough that the worst case — a
         * dense page of a large-format document — stays a couple of megabytes.
         */
        private const val MAX_CACHED_PAGES = 12
        private const val CACHE_CAPACITY = 16
        private const val CACHE_LOAD_FACTOR = 0.75f

        /**
         * Parse a PDF from [input]; [password] unlocks encrypted documents.
         * Heavy — call on an IO dispatcher.
         */
        fun load(
            input: InputStream,
            password: String = "",
        ): PdfTextDocument = PdfTextDocument(PDDocument.load(input, password))
    }
}
