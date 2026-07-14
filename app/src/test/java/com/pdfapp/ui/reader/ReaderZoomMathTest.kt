package com.pdfapp.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderZoomMathTest {
    @Test
    fun `horizontal offset keeps the content under the focus fixed while zooming`() {
        // Content pixel under the focus before zooming, in current-layout space.
        val scroll = 120f
        val focusX = 200f
        val sampleBefore = scroll + focusX

        val newScroll = ReaderZoomMath.horizontalOffset(scroll, focusX, zoomRatio = 2f, pan = 0f)

        // After scaling, that same content sample must still sit under the focus.
        val sampleAfter = newScroll + focusX
        assertThat(sampleAfter).isWithin(1e-3f).of(sampleBefore * 2f)
    }

    @Test
    fun `horizontal pan shifts the offset opposite the finger movement`() {
        val anchored = ReaderZoomMath.horizontalOffset(0f, 0f, zoomRatio = 1f, pan = 0f)
        val panned = ReaderZoomMath.horizontalOffset(0f, 0f, zoomRatio = 1f, pan = 30f)

        assertThat(anchored).isEqualTo(0f)
        assertThat(panned).isEqualTo(-30f)
    }

    @Test
    fun `vertical delta keeps the content under the focus fixed while zooming`() {
        val scroll = 500f
        val focusY = 300f

        val delta = ReaderZoomMath.verticalDelta(scroll, focusY, zoomRatio = 1.5f, pan = 0f)

        // New absolute scroll = old + delta; the content sample under the focus
        // (scroll + focusY) must scale by the zoom ratio.
        val sampleAfter = (scroll + delta) + focusY
        assertThat(sampleAfter).isWithin(1e-2f).of((scroll + focusY) * 1.5f)
    }

    @Test
    fun `vertical delta with no zoom is pure pan`() {
        val delta = ReaderZoomMath.verticalDelta(scroll = 500f, focusY = 300f, zoomRatio = 1f, pan = 45f)

        assertThat(delta).isEqualTo(-45f)
    }
}
