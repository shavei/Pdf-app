package com.pdfapp.core.renderer.text

import com.pdfapp.core.renderer.model.PdfRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Extracts one page's text together with per-character glyph boxes in PDF
 * points, by subclassing PdfBox's [PDFTextStripper] to capture each
 * [TextPosition] instead of only the flattened string.
 *
 * The glyph geometry is the shared engine behind search-match highlighting
 * and text selection: both need to know *where* on the page a run of
 * characters sits.
 */
class PdfTextExtractor {
    /**
     * Extract [pageIndex] (0-based) of [document]. Heavy: parses the page's
     * content stream, so call on a background dispatcher and cache the result.
     */
    fun extract(
        document: PDDocument,
        pageIndex: Int,
    ): PageTextIndex {
        require(pageIndex in 0 until document.numberOfPages) {
            "Page $pageIndex out of bounds (0..${document.numberOfPages - 1})"
        }
        val page = document.getPage(pageIndex)
        // Dir-adjusted stripper coordinates are top-down in the *displayed*
        // (rotation-applied) page frame, so flip with the displayed height —
        // the same frame CoordinateMapper and PdfRenderer bitmaps use.
        val rotated = page.rotation % HALF_TURN_DEGREES != 0
        val displayedHeight = if (rotated) page.cropBox.width else page.cropBox.height
        val collector = GlyphCollector(displayedHeight)
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(document)
        return PageTextIndex(pageIndex, collector.text(), collector.boxes())
    }

    private companion object {
        const val HALF_TURN_DEGREES = 180
    }
}

/**
 * Stripper that records every character with its box. Word and line breaks
 * inserted by the stripper are kept in the text (so search can match across
 * them) but carry no box.
 */
private class GlyphCollector(
    private val displayedHeightPt: Float,
) : PDFTextStripper() {
    private val textBuilder = StringBuilder()
    private val charBoxes = mutableListOf<PdfRect?>()

    init {
        sortByPosition = true
    }

    fun text(): String = textBuilder.toString()

    fun boxes(): List<PdfRect?> = charBoxes

    override fun writeString(
        text: String,
        textPositions: List<TextPosition>,
    ) {
        textPositions.forEach(::addPosition)
    }

    override fun writeWordSeparator() {
        textBuilder.append(' ')
        charBoxes.add(null)
    }

    override fun writeLineSeparator() {
        textBuilder.append('\n')
        charBoxes.add(null)
    }

    private fun addPosition(position: TextPosition) {
        val unicode = position.unicode ?: return
        if (unicode.isEmpty()) return
        val bottom = displayedHeightPt - position.yDirAdj
        val top = bottom + position.heightDir
        // A ligature maps one glyph to several characters; split its advance
        // evenly so every character keeps an approximate box.
        val charWidth = position.widthDirAdj / unicode.length
        unicode.forEachIndexed { i, char ->
            textBuilder.append(char)
            val left = position.xDirAdj + charWidth * i
            charBoxes.add(PdfRect(left, bottom, left + charWidth, top))
        }
    }
}
