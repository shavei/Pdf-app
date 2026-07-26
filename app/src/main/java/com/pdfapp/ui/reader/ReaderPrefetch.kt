package com.pdfapp.ui.reader

/**
 * Which pages to render *before* they scroll into view, so the continuous
 * reader shows a finished page instead of a blank while the renderer catches
 * up. Pure and Compose-free so the windowing is unit-testable on the JVM, like
 * [ReaderZoom] and [ReaderChrome].
 */
object ReaderPrefetch {
    /** Pages warmed ahead of the last visible page (the likely scroll direction). */
    const val AHEAD = 3

    /** Pages warmed behind the first visible page (for scrolling back up). */
    const val BEHIND = 1

    /**
     * Off-screen page indices to warm around the visible range [first]..[last],
     * ordered nearest-first with the pages *ahead* of the viewport prioritised
     * over those behind. The visible pages themselves are excluded — their own
     * composition already requests them — and every index is clamped to
     * `0 until [pageCount]`. Returns empty for an unset/empty range.
     */
    fun window(
        first: Int,
        last: Int,
        pageCount: Int,
        ahead: Int = AHEAD,
        behind: Int = BEHIND,
    ): List<Int> {
        if (pageCount <= 0 || first < 0 || last < first) return emptyList()
        val result = ArrayList<Int>(ahead + behind)
        for (d in 1..ahead) {
            val i = last + d
            if (i < pageCount) result.add(i)
        }
        for (d in 1..behind) {
            val i = first - d
            if (i >= 0) result.add(i)
        }
        return result
    }
}
