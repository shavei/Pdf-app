package com.pdfapp.core.renderer.text

import com.pdfapp.core.renderer.model.PdfRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.text.Bidi

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

/** One extracted character and the box its glyph occupies, in PDF points. */
private class Glyph(
    val char: Char,
    val box: PdfRect,
)

/**
 * Stripper that records every character with its box. Word and line breaks
 * inserted by the stripper are kept in the text (so search can match across
 * them) but carry no box.
 *
 * Characters come out in *logical* (reading) order, not the order the content
 * stream draws them in. That distinction only matters for right-to-left
 * scripts, where it matters completely: a Hebrew line is normally drawn
 * right-to-left, so replaying the draw order yields every word reversed —
 * "בחולה" stored as "הלוחב" — and no typed query can ever match it. PdfBox
 * already resolves the direction for us and hands the result to
 * [writeString] as its `text` argument; the job here is to keep each glyph's
 * box attached to its character while adopting that order.
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
        val drawn = textPositions.flatMap(::glyphsOf)
        (reorderToReadingOrder(drawn, text) ?: drawn).forEach { glyph ->
            textBuilder.append(glyph.char)
            charBoxes.add(glyph.box)
        }
    }

    override fun writeWordSeparator() {
        textBuilder.append(' ')
        charBoxes.add(null)
    }

    override fun writeLineSeparator() {
        textBuilder.append('\n')
        charBoxes.add(null)
    }

    private fun glyphsOf(position: TextPosition): List<Glyph> {
        val unicode = position.unicode ?: return emptyList()
        if (unicode.isEmpty()) return emptyList()
        val bottom = displayedHeightPt - position.yDirAdj
        val top = bottom + position.heightDir
        // A ligature maps one glyph to several characters; split its advance
        // evenly so every character keeps an approximate box.
        val charWidth = position.widthDirAdj / unicode.length
        return unicode.mapIndexed { i, char ->
            val left = position.xDirAdj + charWidth * i
            Glyph(char, PdfRect(left, bottom, left + charWidth, top))
        }
    }

    /**
     * Permute [drawn] into the order of [expected] — PdfBox's own
     * direction-resolved text for this run — so the characters read correctly
     * while every box stays with the character it belongs to.
     *
     * Returns null when the permutation cannot be *proved* to reproduce
     * [expected], which is deliberate: PdfBox also folds ligatures and merges
     * diacritics here, and those change the character count rather than just
     * the order. Rather than guess a box for a character that has none, such a
     * run keeps the drawn order — exactly today's behaviour — so this can only
     * fix RTL text, never disturb the Latin text that already worked.
     */
    private fun reorderToReadingOrder(
        drawn: List<Glyph>,
        expected: String,
    ): List<Glyph>? {
        val asDrawn = StringBuilder(drawn.size).apply { drawn.forEach { append(it.char) } }
        if (asDrawn.contentEquals(expected)) return drawn
        if (drawn.size != expected.length) return null
        val order = readingOrder(asDrawn.toString()) ?: return null
        val reordered = order.map { drawn[it] }
        val reproducesExpected =
            reordered.withIndex().all { (i, glyph) ->
                // Bidi mirroring swaps paired punctuation — "(" drawn becomes
                // ")" read — so those need not match character for character.
                glyph.char == expected[i] ||
                    (Character.isMirrored(glyph.char) && Character.isMirrored(expected[i]))
            }
        // Adopt PdfBox's characters, which carry the mirroring it applied.
        return if (reproducesExpected) {
            reordered.mapIndexed { i, glyph -> Glyph(expected[i], glyph.box) }
        } else {
            null
        }
    }

    /**
     * Indices of [text] in reading order. This mirrors, step for step, the
     * bidi handling PdfBox applies to the string it passes to [writeString] —
     * runs themselves reordered by level, then each right-to-left run walked
     * backwards — because the two results have to agree exactly for the
     * permutation to be accepted. Walking the runs in index order is not
     * enough: a line mixing Hebrew with digits or Latin produces several runs
     * whose *order* changes too, which is precisely where a hand-rolled
     * version drifts from PdfBox and the whole line falls back.
     *
     * Null when the text is single-direction left-to-right, and so already in
     * reading order.
     */
    private fun readingOrder(text: String): List<Int>? {
        val bidi = Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        if (!bidi.isMixed && bidi.baseLevel == 0) return null
        val runCount = bidi.runCount
        val levels = ByteArray(runCount)
        val runs = arrayOfNulls<Any>(runCount)
        for (run in 0 until runCount) {
            levels[run] = bidi.getRunLevel(run).toByte()
            runs[run] = run
        }
        Bidi.reorderVisually(levels, 0, runs, 0, runCount)
        val order = ArrayList<Int>(text.length)
        for (run in 0 until runCount) {
            val index = runs[run] as Int
            val start = bidi.getRunStart(index)
            val limit = bidi.getRunLimit(index)
            if (levels[index].toInt() and 1 != 0) {
                for (i in limit - 1 downTo start) order += i
            } else {
                for (i in start until limit) order += i
            }
        }
        return order
    }
}
