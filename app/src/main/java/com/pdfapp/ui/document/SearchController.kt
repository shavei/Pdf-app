package com.pdfapp.ui.document

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pdfapp.core.renderer.text.TextMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-document search state (plan 2.2): runs the query page by page on IO,
 * publishing matches incrementally so early hits are highlighted while later
 * pages are still being indexed. Next/previous wrap around and navigate the
 * reader to the match's page.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val session: () -> DocumentSession?,
    private val onNavigateToPage: (Int) -> Unit,
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
        matches = emptyList()
        currentIndex = 0
    }

    /** Update the query text and re-run the search. */
    fun submit(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        matches = emptyList()
        currentIndex = 0
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

    private fun moveBy(step: Int) {
        if (matches.isEmpty()) return
        currentIndex = (currentIndex + step + matches.size) % matches.size
        current?.let { onNavigateToPage(it.pageIndex) }
    }

    private suspend fun runSearch(text: String) {
        val document = session() ?: return
        var navigated = false
        for (page in 0 until document.pageCount) {
            val pageMatches =
                withContext(Dispatchers.IO) {
                    document.textDocument().searchPage(page, text)
                }
            if (pageMatches.isNotEmpty()) {
                matches = matches + pageMatches
                if (!navigated) {
                    navigated = true
                    onNavigateToPage(pageMatches.first().pageIndex)
                }
            }
        }
    }
}
