package com.pdfapp.ui.document

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pdfapp.core.renderer.text.TextMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What in-document search needs of an open document: how many pages it has, and
 * the hits on one of them. Narrow on purpose — the whole streaming state machine
 * lives in [SearchController], and a seam this small is what lets a unit test
 * drive it without a `PdfRenderer` (backlog S9). [DocumentSession.searchSource]
 * is the real implementation; it does its parsing on IO, so callers here do not
 * pick a dispatcher.
 */
interface SearchSource {
    val pageCount: Int

    /** Case-insensitive hits for [query] on [pageIndex]. Suspends: parses. */
    suspend fun searchPage(
        pageIndex: Int,
        query: String,
    ): List<TextMatch>
}

/**
 * In-document search state (plan 2.2): runs the query page by page, publishing
 * matches incrementally so early hits are highlighted while later pages are
 * still being indexed. Next/previous wrap around and navigate the reader to the
 * match itself.
 *
 * The scan starts at the page being read and wraps, the way every mainstream
 * viewer does, so searching from page 250 of 300 finds the hit below you rather
 * than throwing you back to page 1. [matches] is still kept in document order —
 * the counter numbers hits the way the document does, not the way the scan
 * happened to reach them — so pages found after the wrap are *inserted* ahead of
 * the ones already listed, and [currentIndex] moves with them to stay on the
 * same hit.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val source: () -> SearchSource?,
    private val startPage: () -> Int = { 0 },
    private val onNavigateToMatch: (TextMatch) -> Unit,
) {
    /** Whether the search bar is showing. */
    var active: Boolean by mutableStateOf(false)
        private set

    var query: String by mutableStateOf("")
        private set

    var matches: List<TextMatch> by mutableStateOf(emptyList())
        private set

    var currentIndex: Int by mutableIntStateOf(0)
        private set

    /** True while pages are still being scanned. */
    var searching: Boolean by mutableStateOf(false)
        private set

    /** True when the scan stopped at [MAX_MATCHES] and the document holds more. */
    var truncated: Boolean by mutableStateOf(false)
        private set

    val matchesByPage by derivedStateOf { matches.groupBy { it.pageIndex } }

    val current: TextMatch? get() = matches.getOrNull(currentIndex)

    private var searchJob: Job? = null

    /**
     * Bumped per submission so a cancelled run cannot clear [searching] out from
     * under the run that replaced it. Without it the *previous* job's `finally`
     * — which resumes on the main thread one turn after the new job has already
     * started — reports "not searching" while the new scan is still going, and
     * the counter reads "0/0" (TalkBack: "No matches") for the whole scan.
     */
    private var searchSeq = 0

    fun open() {
        active = true
    }

    fun close() {
        searchSeq++
        searchJob?.cancel()
        active = false
        searching = false
        query = ""
        reset()
    }

    /** Update the query text and re-run the search. */
    fun submit(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        reset()
        if (newQuery.isBlank()) {
            searchSeq++
            searching = false
            return
        }
        val seq = ++searchSeq
        // Set before launching, so the bar shows progress from the keystroke on
        // rather than from whenever the coroutine happens to be dispatched.
        searching = true
        searchJob =
            scope.launch {
                try {
                    runSearch(newQuery)
                } finally {
                    if (seq == searchSeq) searching = false
                }
            }
    }

    fun next() = moveBy(1)

    fun previous() = moveBy(-1)

    private fun reset() {
        matches = emptyList()
        currentIndex = 0
        truncated = false
    }

    private fun moveBy(step: Int) {
        if (matches.isEmpty()) return
        currentIndex = (currentIndex + step + matches.size) % matches.size
        current?.let(onNavigateToMatch)
    }

    private suspend fun runSearch(text: String) {
        val document = source() ?: return
        val pageCount = document.pageCount
        if (pageCount <= 0) return
        val first = startPage().coerceIn(0, pageCount - 1)
        val found = mutableListOf<TextMatch>()
        var navigated = false
        for (step in 0 until pageCount) {
            val page = (first + step) % pageCount
            val pageMatches = document.searchPage(page, text)
            if (pageMatches.isEmpty()) continue
            // A one-letter query on a long document would otherwise materialise
            // a match per occurrence and hold every one of them (backlog M14).
            val kept = pageMatches.take(MAX_MATCHES - found.size)
            val at = insertionPoint(found, page)
            found.addAll(at, kept)
            if (!navigated) {
                navigated = true
                currentIndex = at
                onNavigateToMatch(kept.first())
            } else if (at <= currentIndex) {
                // Pages reached after the wrap belong *above* the hit we are on.
                // Shifting the index by the block keeps it on the same match
                // instead of letting the insertion slide a different one under
                // the highlight.
                currentIndex += kept.size
            }
            matches = found.toList()
            if (kept.size < pageMatches.size || found.size >= MAX_MATCHES) {
                truncated = true
                return
            }
        }
    }

    private companion object {
        /**
         * How many hits one search keeps. Reaching it stops the scan: past a
         * couple of thousand the list has stopped being something anyone steps
         * through, and the bar says so with a "+" rather than pretending the
         * count is the document's.
         */
        const val MAX_MATCHES = 2_000

        /** Where a block of hits from [page] belongs in a document-ordered list. */
        fun insertionPoint(
            found: List<TextMatch>,
            page: Int,
        ): Int {
            var low = 0
            var high = found.size
            while (low < high) {
                val mid = (low + high) / 2
                if (found[mid].pageIndex < page) low = mid + 1 else high = mid
            }
            return low
        }
    }
}
