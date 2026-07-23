package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Prefetch windowing (reader smoothness): warm the off-screen pages around the
 * viewport, ahead-first, clamped to the document and never including the
 * already-visible pages.
 */
class ReaderPrefetchTest {
    @Test
    fun warmsAheadFirstThenBehind() {
        // Visible 4..6 in a long doc: 3 ahead (7,8,9) before 1 behind (3).
        assertThat(ReaderPrefetch.window(first = 4, last = 6, pageCount = 100))
            .containsExactly(7, 8, 9, 3)
            .inOrder()
    }

    @Test
    fun excludesVisiblePages() {
        assertThat(ReaderPrefetch.window(first = 4, last = 6, pageCount = 100))
            .containsNoneOf(4, 5, 6)
    }

    @Test
    fun clampsAtDocumentEnd() {
        // Last page visible: nothing ahead exists, only the page behind.
        assertThat(ReaderPrefetch.window(first = 9, last = 9, pageCount = 10))
            .containsExactly(8)
    }

    @Test
    fun clampsAtDocumentStart() {
        // First page visible: nothing behind, only pages ahead.
        assertThat(ReaderPrefetch.window(first = 0, last = 0, pageCount = 10))
            .containsExactly(1, 2, 3)
            .inOrder()
    }

    @Test
    fun singlePageDocument_hasNothingToWarm() {
        assertThat(ReaderPrefetch.window(first = 0, last = 0, pageCount = 1)).isEmpty()
    }

    @Test
    fun emptyOrUnsetRange_returnsEmpty() {
        assertThat(ReaderPrefetch.window(first = 0, last = 0, pageCount = 0)).isEmpty()
        assertThat(ReaderPrefetch.window(first = -1, last = -1, pageCount = 10)).isEmpty()
        assertThat(ReaderPrefetch.window(first = 5, last = 4, pageCount = 10)).isEmpty()
    }

    @Test
    fun honoursCustomWindowSizes() {
        assertThat(ReaderPrefetch.window(first = 5, last = 5, pageCount = 100, ahead = 2, behind = 2))
            .containsExactly(6, 7, 4, 3)
            .inOrder()
    }
}
