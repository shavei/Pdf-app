package com.pdfapp.ui.common

/**
 * Screen-reader wording for the reader and editor chrome (mobile-ui-plan Phase
 * F.2). Kept free of Compose — like the reader's own `ReaderChrome`,
 * `ReaderBack` and `ReaderLayout` rules — so the phrasing is unit-testable on
 * the JVM and the controls that exist twice (bottom bar *and* nav rail, modal
 * sheet *and* docked pane) are announced identically.
 *
 * Labels exist even where the control already shows text, because the chrome is
 * dense with glyph shorthand — "5 / 120", "3/12" — that TalkBack reads out as
 * punctuation instead of as a position.
 */
object ReaderSemantics {
    /**
     * Longest slice of extracted page text we hand to a screen reader. A page
     * is one accessibility node, so its whole text is one utterance; the cap
     * keeps a dense page from producing an unbounded description.
     */
    const val MAX_PAGE_TEXT_CHARS: Int = 4_000

    /**
     * A rendered page's description: its position always, plus the extracted
     * text when there is a screen reader to read it (plan.md's "expose
     * extracted page text to TalkBack"). [pageText] is null until extraction
     * finishes — and stays null when no screen reader is running, so the IO is
     * never spent.
     */
    fun pageLabel(
        pageIndex: Int,
        pageCount: Int,
        pageText: String? = null,
    ): String {
        val position = "Page ${pageIndex + 1} of $pageCount"
        val body = pageText?.let { condense(it, MAX_PAGE_TEXT_CHARS) }.orEmpty()
        return if (body.isEmpty()) position else "$position. $body"
    }

    /** The tap-to-jump page chip on the bottom bar, nav rail and edit bar. */
    fun pageChipLabel(
        pageIndex: Int,
        pageCount: Int,
    ): String = "Page ${pageIndex + 1} of $pageCount, go to page"

    /**
     * One cell of the thumbnail grid, in the sheet or the docked pane. Phrased
     * as the action, not the position: a thumbnail is a jump control, and
     * "Page 2 of 9" would be the same thing a *rendered page* announces — two
     * different controls a screen reader user could not tell apart.
     */
    fun thumbnailLabel(
        pageIndex: Int,
        pageCount: Int,
        isCurrent: Boolean,
    ): String = "Go to page ${pageIndex + 1} of $pageCount" + if (isCurrent) ", current page" else ""

    /** The immersive chrome toggle, offered on each page as a click action. */
    fun chromeToggleLabel(chromeVisible: Boolean): String =
        if (chromeVisible) "Hide reader controls" else "Show reader controls"

    /**
     * The visible search counter — deliberately terse, it shares a crowded bar.
     * [truncated] marks a count the scan stopped at rather than one the document
     * ended at, shown as the "300+" convention every search bar uses.
     */
    fun searchCounterText(
        currentIndex: Int,
        matchCount: Int,
        searching: Boolean,
        hasQuery: Boolean,
        truncated: Boolean = false,
    ): String =
        when {
            matchCount > 0 -> "${currentIndex + 1}/$matchCount" + if (truncated) "+" else ""
            searching -> "…"
            !hasQuery -> ""
            else -> "0/0"
        }

    /**
     * The spoken form of that counter; blank when there is nothing to announce.
     * The "+" is spelled out — TalkBack reads it as "plus", which says nothing
     * about what it qualifies.
     */
    fun searchCounterLabel(
        currentIndex: Int,
        matchCount: Int,
        searching: Boolean,
        hasQuery: Boolean,
        truncated: Boolean = false,
    ): String =
        when {
            matchCount > 0 && truncated -> "Match ${currentIndex + 1} of the first $matchCount"
            matchCount > 0 -> "Match ${currentIndex + 1} of $matchCount"
            searching -> "Searching"
            !hasQuery -> ""
            else -> "No matches"
        }

    /** Announced when a long-press selection latches, via a polite live region. */
    fun selectionLabel(text: String): String = "Selected text: ${condense(text, MAX_SELECTION_CHARS)}"

    /** One row of the home screen's recent-files list. */
    fun recentFileLabel(
        displayName: String,
        pageCount: Int,
        lastPageIndex: Int,
    ): String = "$displayName, $pageCount pages, last read page ${lastPageIndex + 1}"

    /**
     * PDF text arrives with the layout's line breaks and column padding baked
     * in; collapsing it to single spaces stops a screen reader pausing at every
     * wrapped line.
     */
    private fun condense(
        text: String,
        limit: Int,
    ): String {
        val flat = text.replace(WHITESPACE, " ").trim()
        return if (flat.length <= limit) flat else flat.take(limit).trimEnd() + "…"
    }

    private const val MAX_SELECTION_CHARS = 200
    private val WHITESPACE = Regex("\\s+")
}
