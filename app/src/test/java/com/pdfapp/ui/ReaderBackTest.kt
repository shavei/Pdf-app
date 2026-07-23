package com.pdfapp.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Predictive-back unwind order (mobile-ui-plan Phase D.3): back peels search,
 * then edit mode, then immersive chrome, and only then defers to the system.
 */
class ReaderBackTest {
    @Test
    fun noSession_defersToSystem() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = false,
                searchActive = false,
                mode = ViewerMode.READ,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.SYSTEM)
    }

    @Test
    fun plainReading_defersToSystem() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                mode = ViewerMode.READ,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.SYSTEM)
    }

    @Test
    fun search_closesFirst_evenInEditMode() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = true,
                mode = ViewerMode.EDIT,
                chromeVisible = false,
            ),
        ).isEqualTo(ReaderBackAction.CLOSE_SEARCH)
    }

    @Test
    fun editMode_exitsToRead() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                mode = ViewerMode.EDIT,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.EXIT_EDIT)
    }

    @Test
    fun immersiveReading_restoresChromeBeforeExiting() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                mode = ViewerMode.READ,
                chromeVisible = false,
            ),
        ).isEqualTo(ReaderBackAction.SHOW_CHROME)
    }
}
