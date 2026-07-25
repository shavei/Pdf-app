package com.pdfapp.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase F.2 (mobile-ui-plan): the screen-reader wording. These are the strings
 * TalkBack actually speaks, so they are asserted verbatim — a control that
 * exists twice (bottom bar and nav rail, sheet and docked pane) must not drift
 * into two different announcements.
 */
class ReaderSemanticsTest {
    @Test
    fun pageLabel_without_text_announces_position_only() {
        assertThat(ReaderSemantics.pageLabel(pageIndex = 0, pageCount = 12))
            .isEqualTo("Page 1 of 12")
    }

    @Test
    fun pageLabel_appends_the_extracted_text_when_present() {
        assertThat(ReaderSemantics.pageLabel(2, 12, "Hello world"))
            .isEqualTo("Page 3 of 12. Hello world")
    }

    @Test
    fun pageLabel_condenses_the_layout_whitespace_of_extracted_text() {
        // PDF text arrives with the page's line breaks and column padding in it;
        // a screen reader should not pause at every wrapped line.
        assertThat(ReaderSemantics.pageLabel(0, 1, "  Signed\n\nby\t\tme  "))
            .isEqualTo("Page 1 of 1. Signed by me")
    }

    @Test
    fun pageLabel_ignores_text_that_is_only_whitespace() {
        assertThat(ReaderSemantics.pageLabel(0, 3, "   \n  ")).isEqualTo("Page 1 of 3")
    }

    @Test
    fun pageLabel_caps_a_dense_page_so_one_node_cannot_grow_unbounded() {
        val label = ReaderSemantics.pageLabel(0, 1, "x".repeat(ReaderSemantics.MAX_PAGE_TEXT_CHARS * 2))
        assertThat(label).endsWith("…")
        assertThat(label.length)
            .isAtMost("Page 1 of 1. ".length + ReaderSemantics.MAX_PAGE_TEXT_CHARS + 1)
    }

    @Test
    fun pageChipLabel_speaks_the_position_the_glyphs_only_imply() {
        assertThat(ReaderSemantics.pageChipLabel(4, 120)).isEqualTo("Page 5 of 120, go to page")
    }

    @Test
    fun thumbnailLabel_marks_the_current_page() {
        assertThat(ReaderSemantics.thumbnailLabel(1, 9, isCurrent = false)).isEqualTo("Page 2 of 9")
        assertThat(ReaderSemantics.thumbnailLabel(1, 9, isCurrent = true))
            .isEqualTo("Page 2 of 9, current page")
    }

    @Test
    fun chromeToggleLabel_names_the_result_of_the_action_not_the_state() {
        assertThat(ReaderSemantics.chromeToggleLabel(chromeVisible = true))
            .isEqualTo("Hide reader controls")
        assertThat(ReaderSemantics.chromeToggleLabel(chromeVisible = false))
            .isEqualTo("Show reader controls")
    }

    @Test
    fun searchCounter_shows_terse_text_and_speaks_the_long_form() {
        val visible =
            ReaderSemantics.searchCounterText(
                currentIndex = 2,
                matchCount = 12,
                searching = false,
                hasQuery = true,
            )
        val spoken =
            ReaderSemantics.searchCounterLabel(
                currentIndex = 2,
                matchCount = 12,
                searching = false,
                hasQuery = true,
            )
        assertThat(visible).isEqualTo("3/12")
        assertThat(spoken).isEqualTo("Match 3 of 12")
    }

    @Test
    fun searchCounter_covers_the_searching_empty_and_no_match_states() {
        fun visible(
            matches: Int,
            searching: Boolean,
            hasQuery: Boolean,
        ) = ReaderSemantics.searchCounterText(0, matches, searching, hasQuery)

        fun spoken(
            matches: Int,
            searching: Boolean,
            hasQuery: Boolean,
        ) = ReaderSemantics.searchCounterLabel(0, matches, searching, hasQuery)

        assertThat(visible(0, true, true)).isEqualTo("…")
        assertThat(spoken(0, true, true)).isEqualTo("Searching")
        assertThat(visible(0, false, true)).isEqualTo("0/0")
        assertThat(spoken(0, false, true)).isEqualTo("No matches")
        // Nothing typed yet: the counter renders nothing, so it says nothing.
        assertThat(visible(0, false, false)).isEmpty()
        assertThat(spoken(0, false, false)).isEmpty()
    }

    @Test
    fun selectionLabel_condenses_and_caps_the_selected_text() {
        assertThat(ReaderSemantics.selectionLabel("two\nlines")).isEqualTo("Selected text: two lines")
        assertThat(ReaderSemantics.selectionLabel("y".repeat(1_000))).endsWith("…")
    }

    @Test
    fun recentFileLabel_replaces_the_row_separator_with_words() {
        assertThat(
            ReaderSemantics.recentFileLabel(
                displayName = "contract.pdf",
                pageCount = 12,
                lastPageIndex = 2,
            ),
        ).isEqualTo("contract.pdf, 12 pages, last read page 3")
    }
}
