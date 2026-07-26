package com.pdfapp.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Predictive-back unwind order (mobile-ui-plan Phase D.3): back peels search,
 * then form filling, then edit mode, then immersive chrome, and only then defers
 * to the system.
 */
class ReaderBackTest {
    @Test
    fun noSession_defersToSystem() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = false,
                searchActive = false,
                formActive = false,
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
                formActive = false,
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
                formActive = false,
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
                formActive = false,
                mode = ViewerMode.EDIT,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.EXIT_EDIT)
    }

    @Test
    fun formFilling_closesTheFillLayerBeforeLeavingTheDocument() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                formActive = true,
                mode = ViewerMode.READ,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.CLOSE_FORM)
    }

    @Test
    fun search_closesBeforeTheFillLayer() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = true,
                formActive = true,
                mode = ViewerMode.READ,
                chromeVisible = true,
            ),
        ).isEqualTo(ReaderBackAction.CLOSE_SEARCH)
    }

    @Test
    fun formFilling_outranksImmersiveChrome() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                formActive = true,
                mode = ViewerMode.READ,
                chromeVisible = false,
            ),
        ).isEqualTo(ReaderBackAction.CLOSE_FORM)
    }

    @Test
    fun immersiveReading_restoresChromeBeforeExiting() {
        assertThat(
            ReaderBack.actionFor(
                hasSession = true,
                searchActive = false,
                formActive = false,
                mode = ViewerMode.READ,
                chromeVisible = false,
            ),
        ).isEqualTo(ReaderBackAction.SHOW_CHROME)
    }
}
