package com.pdfapp.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Immersive-reading visibility rules (mobile-ui-plan Phase B): the reader's
 * primary-action surface tracks `chromeVisible` while reading, and steps aside
 * for search / edit / the home screen exactly as the top bar does.
 */
class ReaderChromeTest {
    @Test
    fun actions_shows_only_while_reading_with_chrome_up() {
        assertThat(
            ReaderChrome.actionsShown(
                chromeVisible = true,
                searchActive = false,
                mode = ViewerMode.READ,
                hasSession = true,
            ),
        ).isTrue()
    }

    @Test
    fun actions_hides_in_immersive_reading() {
        assertThat(
            ReaderChrome.actionsShown(
                chromeVisible = false,
                searchActive = false,
                mode = ViewerMode.READ,
                hasSession = true,
            ),
        ).isFalse()
    }

    @Test
    fun actions_hides_during_search_edit_and_home() {
        assertThat(
            ReaderChrome.actionsShown(true, searchActive = true, ViewerMode.READ, hasSession = true),
        ).isFalse()
        assertThat(
            ReaderChrome.actionsShown(true, searchActive = false, ViewerMode.EDIT, hasSession = true),
        ).isFalse()
        assertThat(
            ReaderChrome.actionsShown(true, searchActive = false, ViewerMode.READ, hasSession = false),
        ).isFalse()
    }

    @Test
    fun topBar_hides_only_in_immersive_reading() {
        // Chrome up → shown; chrome down while purely reading → hidden.
        assertThat(
            ReaderChrome.topBarShown(true, searchActive = false, ViewerMode.READ, hasSession = true),
        ).isTrue()
        assertThat(
            ReaderChrome.topBarShown(false, searchActive = false, ViewerMode.READ, hasSession = true),
        ).isFalse()
        // Search keeps the top bar (it hosts the field) even with chrome "down".
        assertThat(
            ReaderChrome.topBarShown(false, searchActive = true, ViewerMode.READ, hasSession = true),
        ).isTrue()
        // Edit mode and the home screen are never immersive.
        assertThat(
            ReaderChrome.topBarShown(false, searchActive = false, ViewerMode.EDIT, hasSession = true),
        ).isTrue()
        assertThat(
            ReaderChrome.topBarShown(false, searchActive = false, ViewerMode.READ, hasSession = false),
        ).isTrue()
    }
}
