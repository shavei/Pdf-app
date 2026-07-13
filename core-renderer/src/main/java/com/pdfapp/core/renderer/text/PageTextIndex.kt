package com.pdfapp.core.renderer.text

import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PdfRect

/** One search hit: the matched character [range] and its highlight [boxes]. */
data class TextMatch(
    val pageIndex: Int,
    val range: IntRange,
    val boxes: List<PdfRect>,
)

/**
 * The extracted text of one page plus a per-character glyph box (null for the
 * separators the stripper synthesises). Pure geometry/string logic — no
 * Android or PdfBox types — so search and selection are JVM-testable.
 */
class PageTextIndex(
    val pageIndex: Int,
    val text: String,
    private val charBoxes: List<PdfRect?>,
) {
    init {
        require(text.length == charBoxes.size) {
            "text (${text.length}) and boxes (${charBoxes.size}) must be parallel"
        }
    }

    /** Case-insensitive substring search; matches may span word/line breaks. */
    fun search(query: String): List<TextMatch> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<TextMatch>()
        var from = 0
        while (true) {
            val start = text.indexOf(query, from, ignoreCase = true)
            if (start < 0) break
            val range = start until start + query.length
            matches += TextMatch(pageIndex, range, boxesFor(range))
            from = start + 1
        }
        return matches
    }

    /** Highlight quads for [range]: consecutive glyph boxes merged per line. */
    fun boxesFor(range: IntRange): List<PdfRect> {
        val quads = mutableListOf<PdfRect>()
        var current: PdfRect? = null
        for (i in range) {
            val box = charBoxes.getOrNull(i) ?: continue
            val merged = current
            current =
                when {
                    merged == null -> box
                    sameLine(merged, box) -> merged.union(box)
                    else -> {
                        quads += merged
                        box
                    }
                }
        }
        current?.let { quads += it }
        return quads
    }

    /** The characters covered by [range], clamped to the page text. */
    fun textIn(range: IntRange): String {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        return text.substring(start, end)
    }

    /** Char range of the whitespace-delimited word under [point], if any. */
    fun wordRangeAt(point: PdfPoint): IntRange? {
        val index = charIndexAt(point) ?: return null
        if (text[index].isWhitespace()) return null
        var start = index
        var end = index
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length - 1 && !text[end + 1].isWhitespace()) end++
        return start..end
    }

    /** Index of the glyph whose box contains [point], or null. */
    fun charIndexAt(point: PdfPoint): Int? {
        val exact = charBoxes.indexOfFirst { it?.contains(point) == true }
        return if (exact >= 0) exact else null
    }

    /**
     * Index of the glyph nearest [point] within [maxDistancePt] of its box
     * centre — forgiving hit-testing for drag-selection handles.
     */
    fun charIndexNear(
        point: PdfPoint,
        maxDistancePt: Float = DEFAULT_NEAR_DISTANCE_PT,
    ): Int? {
        charIndexAt(point)?.let { return it }
        var bestIndex: Int? = null
        var bestDistance = maxDistancePt
        charBoxes.forEachIndexed { i, box ->
            if (box == null) return@forEachIndexed
            val center = box.center
            val distance = kotlin.math.hypot(point.x - center.x, point.y - center.y)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Char range between the glyphs nearest the two drag [anchor]/[focus]
     * points (either order), snapped outward to whole words.
     */
    fun selectionBetween(
        anchor: PdfPoint,
        focus: PdfPoint,
    ): IntRange? {
        val a = charIndexNear(anchor) ?: return null
        val b = charIndexNear(focus) ?: return null
        val range = minOf(a, b)..maxOf(a, b)
        var start = range.first
        var end = range.last
        while (start > 0 && !text[start - 1].isWhitespace() && !text[start].isWhitespace()) start--
        while (end < text.length - 1 && !text[end + 1].isWhitespace() && !text[end].isWhitespace()) end++
        return start..end
    }

    private fun sameLine(
        a: PdfRect,
        b: PdfRect,
    ): Boolean {
        val overlap = minOf(a.top, b.top) - maxOf(a.bottom, b.bottom)
        return overlap > MIN_LINE_OVERLAP_FRACTION * minOf(a.height, b.height)
    }

    private companion object {
        const val MIN_LINE_OVERLAP_FRACTION = 0.5f
        const val DEFAULT_NEAR_DISTANCE_PT = 24f
    }
}
