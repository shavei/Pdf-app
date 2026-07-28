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

    /**
     * Case-insensitive search that matches the way the page *reads* rather than
     * the way its producer happened to emit it.
     *
     * Extracted PDF text carries the layout's own breaks: a phrase that wraps
     * arrives as `"over the\nlazy dog"`, a word broken by justification arrives
     * as `"hyphen-\nated"`, and non-breaking spaces arrive as U+00A0. A literal
     * `indexOf` therefore misses `"the lazy"` and `"hyphenated"` on a page that
     * plainly contains both. Matching runs against a normalised projection
     * instead — whitespace runs collapsed to one space, end-of-line hyphens
     * joined — with an index map back to the original characters, so the
     * reported [TextMatch.range] and its highlight [boxes][boxesFor] still refer
     * to real glyphs and still split correctly per line.
     *
     * Matches do not overlap: the scan resumes past each hit, so `"aa"` finds
     * two matches in `"aaaa"`, not three.
     *
     * Known limit: joining an end-of-line hyphen means a genuine compound
     * broken across lines (`"well-\nknown"`) is found as `"wellknown"` rather
     * than `"well-known"`. That is the same trade every mainstream viewer
     * makes — soft breaks are far commoner than broken compounds.
     */
    fun search(query: String): List<TextMatch> {
        val needle = normalizeQuery(query)
        if (needle.isEmpty()) return emptyList()
        val haystack = normalized
        val matches = mutableListOf<TextMatch>()
        var from = 0
        while (from <= haystack.text.length - needle.length) {
            val start = haystack.text.indexOf(needle, from, ignoreCase = true)
            if (start < 0) break
            val range = haystack.origin[start]..haystack.origin[start + needle.length - 1]
            matches += TextMatch(pageIndex, range, boxesFor(range))
            from = start + needle.length
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

    /**
     * The searchable projection of [text], built once on first search. [origin]
     * maps each of its characters back to the index in [text] it came from, so a
     * hit found here resolves to a range over the real glyphs.
     */
    private class NormalizedText(
        val text: String,
        val origin: IntArray,
    )

    private val normalized: NormalizedText by lazy { normalize(text) }

    private companion object {
        const val MIN_LINE_OVERLAP_FRACTION = 0.5f
        const val DEFAULT_NEAR_DISTANCE_PT = 24f
        const val SOFT_HYPHEN = '\u00AD'
        const val UNICODE_HYPHEN = '\u2010'

        /** Whitespace *and* the space separators (NBSP, thin space) PDFs emit. */
        fun isSpace(char: Char): Boolean = char.isWhitespace() || Character.isSpaceChar(char)

        fun isHyphen(char: Char): Boolean = char == '-' || char == UNICODE_HYPHEN

        /**
         * Collapse whitespace runs to a single space, drop soft hyphens, and
         * join words split by an end-of-line hyphen, recording where every
         * surviving character came from.
         */
        fun normalize(source: String): NormalizedText {
            val out = StringBuilder(source.length)
            // Never longer than the source: each emitted char consumes at least one.
            val origin = IntArray(source.length)
            var index = 0
            var pendingSpaceAt = -1
            while (index < source.length) {
                val char = source[index]
                when {
                    char == SOFT_HYPHEN -> index++
                    isHyphen(char) && breaksLineAfter(source, index) ->
                        index = skipSpace(source, index + 1)
                    isSpace(char) -> {
                        if (pendingSpaceAt < 0) pendingSpaceAt = index
                        index++
                    }
                    else -> {
                        // Leading whitespace is dropped rather than emitted, so a
                        // query never has to know how the page is indented.
                        if (pendingSpaceAt >= 0 && out.isNotEmpty()) {
                            origin[out.length] = pendingSpaceAt
                            out.append(' ')
                        }
                        pendingSpaceAt = -1
                        origin[out.length] = index
                        out.append(char)
                        index++
                    }
                }
            }
            return NormalizedText(out.toString(), origin.copyOf(out.length))
        }

        /** True when only spaces separate the hyphen at [index] from a line break. */
        fun breaksLineAfter(
            source: String,
            index: Int,
        ): Boolean {
            var i = index + 1
            while (i < source.length && isSpace(source[i])) {
                if (source[i] == '\n') return true
                i++
            }
            return false
        }

        fun skipSpace(
            source: String,
            from: Int,
        ): Int {
            var i = from
            while (i < source.length && isSpace(source[i])) i++
            return i
        }

        /**
         * The query gets the same whitespace treatment as the page, so a typed
         * phrase matches regardless of how the user spaced it. Hyphens the user
         * typed are kept — only the page's line-end ones are joined.
         */
        fun normalizeQuery(query: String): String {
            val out = StringBuilder(query.length)
            var pendingSpace = false
            query.forEach { char ->
                when {
                    char == SOFT_HYPHEN -> Unit
                    isSpace(char) -> pendingSpace = true
                    else -> {
                        if (pendingSpace && out.isNotEmpty()) out.append(' ')
                        pendingSpace = false
                        out.append(char)
                    }
                }
            }
            return out.toString()
        }
    }
}
