package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Double-tap zoom toggle math (mobile-ui-plan Phase D.1): a double-tap
 * ping-pongs between fit-width and the comfortable reading zoom regardless of
 * whatever pinch zoom the user was left on.
 */
class ReaderZoomTest {
    @Test
    fun fromFitWidth_zoomsIn() {
        assertThat(ReaderZoom.doubleTapTarget(ReaderZoom.FIT_WIDTH))
            .isEqualTo(ReaderZoom.DOUBLE_TAP_ZOOM)
    }

    @Test
    fun fromZoomedIn_returnsToFitWidth() {
        assertThat(ReaderZoom.doubleTapTarget(ReaderZoom.DOUBLE_TAP_ZOOM))
            .isEqualTo(ReaderZoom.FIT_WIDTH)
    }

    @Test
    fun anyZoomAbovePreset_returnsToFitWidth() {
        // Deep pinch zoom still toggles back to fit on the next double-tap.
        assertThat(ReaderZoom.doubleTapTarget(8f)).isEqualTo(ReaderZoom.FIT_WIDTH)
        assertThat(ReaderZoom.doubleTapTarget(1.5f)).isEqualTo(ReaderZoom.FIT_WIDTH)
    }

    @Test
    fun withinFitEpsilon_zoomsIn() {
        // A hair above 1.0 (rounding drift) is still "fit width" and zooms in.
        assertThat(ReaderZoom.doubleTapTarget(1.0005f)).isEqualTo(ReaderZoom.DOUBLE_TAP_ZOOM)
    }

    @Test
    fun zoomedOutBelowFit_zoomsIn() {
        // Pinched smaller than fit-width: double-tap jumps up to the reading zoom.
        assertThat(ReaderZoom.doubleTapTarget(0.5f)).isEqualTo(ReaderZoom.DOUBLE_TAP_ZOOM)
    }
}
