package com.pdfapp.ui.document

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfRect
import com.pdfapp.core.renderer.text.TextMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The search bar's whole state machine, driven through the [SearchSource] seam
 * (backlog S9) so it can be exercised without a `PdfRenderer`: the streaming
 * scan and where it starts, document-ordered results, next/previous, the match
 * cap, and the progress flag the counter reads every frame.
 *
 * The flag is the part worth pinning down. A wrong value there is
 * indistinguishable to a user from search being broken: the bar reads "0/0" and
 * TalkBack says "No matches" while a scan is still running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val navigatedTo = mutableListOf<TextMatch>()

    /** A document whose page N contains [pages]`[N]` occurrences of anything. */
    private class FakePages(
        private val hitsPerPage: Map<Int, Int>,
        override val pageCount: Int,
    ) : SearchSource {
        override suspend fun searchPage(
            pageIndex: Int,
            query: String,
        ): List<TextMatch> =
            List(hitsPerPage[pageIndex] ?: 0) { hit ->
                TextMatch(pageIndex, hit..hit, listOf(PdfRect(0f, 0f, 1f, 1f)))
            }
    }

    private fun controller(
        scope: TestScope,
        source: SearchSource? = null,
        startPage: Int = 0,
    ) = SearchController(scope, { source }, { startPage }, navigatedTo::add)

    private fun pages(
        pageCount: Int,
        vararg hits: Pair<Int, Int>,
    ) = FakePages(hits.toMap(), pageCount)

    @Test
    fun `progress is reported from the keystroke, not from the dispatch`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this)

            search.submit("needle")

            // Nothing has been dispatched yet — the bar must already say so,
            // rather than showing "no matches" until the coroutine gets a turn.
            assertThat(search.searching).isTrue()

            advanceUntilIdle()
            assertThat(search.searching).isFalse()
        }

    @Test
    fun `a superseded search cannot clear the progress flag`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this)

            search.submit("first")
            search.submit("second")

            // The first submission is cancelled here; only the newest one owns
            // the flag, so it stays set for the scan that is actually running.
            assertThat(search.query).isEqualTo("second")
            assertThat(search.searching).isTrue()

            advanceUntilIdle()
            assertThat(search.searching).isFalse()
        }

    @Test
    fun `a blank query clears the results without searching`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(3, 0 to 1))

            search.submit("needle")
            search.submit("   ")

            assertThat(search.searching).isFalse()
            assertThat(search.matches).isEmpty()

            advanceUntilIdle()
            assertThat(search.searching).isFalse()
            assertThat(search.matches).isEmpty()
        }

    @Test
    fun `close resets the bar back to plain reading`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(3, 0 to 2))
            search.open()
            search.submit("needle")

            search.close()

            assertThat(search.active).isFalse()
            assertThat(search.query).isEmpty()
            assertThat(search.matches).isEmpty()
            assertThat(search.currentIndex).isEqualTo(0)
            assertThat(search.searching).isFalse()
            assertThat(search.truncated).isFalse()

            advanceUntilIdle()
            assertThat(search.matches).isEmpty()
            assertThat(search.searching).isFalse()
        }

    @Test
    fun `next and previous do nothing while there is nothing to step through`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this)

            search.next()
            search.previous()

            assertThat(search.currentIndex).isEqualTo(0)
            assertThat(search.current).isNull()
            assertThat(navigatedTo).isEmpty()
        }

    @Test
    fun `every hit in the document is collected, in document order`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(5, 1 to 2, 3 to 1, 4 to 3))

            search.submit("needle")
            advanceUntilIdle()

            assertThat(search.matches.map { it.pageIndex })
                .containsExactly(1, 1, 3, 4, 4, 4)
                .inOrder()
            assertThat(search.matchesByPage.keys).containsExactly(1, 3, 4)
            assertThat(search.truncated).isFalse()
        }

    @Test
    fun `the scan starts at the page being read rather than at page one`() =
        runTest(StandardTestDispatcher()) {
            // Hits above *and* below the reader: the one below is the one a
            // viewer jumps to (backlog M13).
            val search = controller(this, pages(10, 1 to 1, 7 to 1), startPage = 5)

            search.submit("needle")
            advanceUntilIdle()

            assertThat(navigatedTo.map { it.pageIndex }).containsExactly(7)
            assertThat(search.current?.pageIndex).isEqualTo(7)
        }

    @Test
    fun `wrapping past the end keeps the counter numbering the document, not the scan`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(10, 1 to 1, 4 to 1, 7 to 2), startPage = 5)

            search.submit("needle")
            advanceUntilIdle()

            // Scanned 7, then 1 and 4 after the wrap — but listed as the
            // document reads, with the current match still the one it jumped to.
            assertThat(search.matches.map { it.pageIndex })
                .containsExactly(1, 4, 7, 7)
                .inOrder()
            assertThat(search.currentIndex).isEqualTo(2)
            assertThat(search.current?.pageIndex).isEqualTo(7)
        }

    @Test
    fun `a search that wraps finds the hits above the reader too`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(10, 2 to 1), startPage = 8)

            search.submit("needle")
            advanceUntilIdle()

            assertThat(search.matches.map { it.pageIndex }).containsExactly(2)
            assertThat(search.currentIndex).isEqualTo(0)
        }

    @Test
    fun `next and previous step through the hits and wrap around`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(4, 0 to 1, 2 to 2))

            search.submit("needle")
            advanceUntilIdle()
            navigatedTo.clear()

            search.next()
            search.next()
            search.next()

            assertThat(navigatedTo.map { it.pageIndex }).containsExactly(2, 2, 0).inOrder()
            assertThat(search.currentIndex).isEqualTo(0)

            search.previous()

            assertThat(search.currentIndex).isEqualTo(2)
            assertThat(navigatedTo.last().pageIndex).isEqualTo(2)
        }

    @Test
    fun `an unbounded query stops at the cap and says the count is partial`() =
        runTest(StandardTestDispatcher()) {
            // Far more hits than the cap, spread over pages the scan will not
            // reach once it has filled up (backlog M14).
            val perPage = 900
            val search = controller(this, pages(20, 0 to perPage, 1 to perPage, 2 to perPage, 19 to perPage))

            search.submit("e")
            advanceUntilIdle()

            assertThat(search.matches).hasSize(2_000)
            assertThat(search.truncated).isTrue()
            assertThat(search.matches.map { it.pageIndex }.distinct()).containsExactly(0, 1, 2)
            assertThat(search.searching).isFalse()
        }

    @Test
    fun `a document with no pages is not searched`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this, pages(0))

            search.submit("needle")
            advanceUntilIdle()

            assertThat(search.matches).isEmpty()
            assertThat(search.searching).isFalse()
            assertThat(navigatedTo).isEmpty()
        }
}
