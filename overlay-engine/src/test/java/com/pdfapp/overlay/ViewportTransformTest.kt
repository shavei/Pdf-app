package com.pdfapp.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ViewportTransformTest {
    private val transform = ViewportTransform()

    @Test
    fun `identity while view or content size is unknown`() {
        transform.setContentSize(200f, 200f)

        assertThat(transform.scale).isEqualTo(1f)
        assertThat(transform.toContentX(37f)).isEqualTo(37f)
        assertThat(transform.toContentY(41f)).isEqualTo(41f)
    }

    @Test
    fun `page smaller than view is centred at fit scale`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(100f, 200f)

        // Fit scale is min(400/100, 400/200) = 2 → scaled content is 200x400.
        assertThat(transform.scale).isEqualTo(2f)
        assertThat(transform.offsetX).isEqualTo(100f)
        assertThat(transform.offsetY).isEqualTo(0f)
    }

    @Test
    fun `pinch zooms about the pivot and maps view points back to content`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(400f, 400f)

        // Content point under the pivot before zooming.
        val pivotContentX = transform.toContentX(100f)
        transform.pinch(pivotX = 100f, pivotY = 100f, scaleFactor = 2f, deltaX = 0f, deltaY = 0f)

        assertThat(transform.zoom).isEqualTo(2f)
        assertThat(transform.toContentX(100f)).isWithin(1e-4f).of(pivotContentX)
    }

    @Test
    fun `zoom is clamped to its bounds`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(400f, 400f)

        transform.pinch(200f, 200f, scaleFactor = 100f, deltaX = 0f, deltaY = 0f)
        assertThat(transform.zoom).isEqualTo(8f)

        transform.pinch(200f, 200f, scaleFactor = 0.001f, deltaX = 0f, deltaY = 0f)
        assertThat(transform.zoom).isEqualTo(1f)
    }

    @Test
    fun `pan is clamped so the page never leaves the view`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(400f, 400f)
        transform.pinch(200f, 200f, scaleFactor = 2f, deltaX = 0f, deltaY = 0f)

        transform.panBy(-10_000f, 10_000f)

        // Scaled content is 800x800 in a 400x400 view: offsets stay in [-400, 0].
        assertThat(transform.offsetX).isEqualTo(-400f)
        assertThat(transform.offsetY).isEqualTo(0f)
    }

    @Test
    fun `pan while fully zoomed out keeps the page centred`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(100f, 200f)

        transform.panBy(50f, -75f)

        assertThat(transform.offsetX).isEqualTo(100f)
        assertThat(transform.offsetY).isEqualTo(0f)
    }

    @Test
    fun `new content resets zoom and pan`() {
        transform.setViewSize(400f, 400f)
        transform.setContentSize(400f, 400f)
        transform.pinch(0f, 0f, scaleFactor = 4f, deltaX = -50f, deltaY = -50f)

        transform.setContentSize(200f, 400f)

        assertThat(transform.zoom).isEqualTo(1f)
        assertThat(transform.offsetX).isEqualTo(100f)
        assertThat(transform.offsetY).isEqualTo(0f)
    }
}
