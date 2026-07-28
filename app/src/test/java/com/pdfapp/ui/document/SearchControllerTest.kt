package com.pdfapp.ui.document

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The search bar's own state machine. No document is opened: with no session
 * `runSearch` is a no-op, which leaves exactly the bookkeeping the search bar
 * reads every frame — the progress flag, the query, and what `close()` resets.
 *
 * The flag is the part worth pinning down. It drives the counter, and a wrong
 * value there is indistinguishable to a user from search being broken: the bar
 * reads "0/0" and TalkBack says "No matches" while a scan is still running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val navigatedTo = mutableListOf<Int>()

    private fun controller(scope: TestScope) = SearchController(scope, { null }, navigatedTo::add)

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
            val search = controller(this)

            search.submit("needle")
            search.submit("   ")

            assertThat(search.searching).isFalse()
            assertThat(search.matches).isEmpty()

            advanceUntilIdle()
            assertThat(search.searching).isFalse()
        }

    @Test
    fun `close resets the bar back to plain reading`() =
        runTest(StandardTestDispatcher()) {
            val search = controller(this)
            search.open()
            search.submit("needle")

            search.close()

            assertThat(search.active).isFalse()
            assertThat(search.query).isEmpty()
            assertThat(search.matches).isEmpty()
            assertThat(search.currentIndex).isEqualTo(0)
            assertThat(search.searching).isFalse()

            advanceUntilIdle()
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
}
