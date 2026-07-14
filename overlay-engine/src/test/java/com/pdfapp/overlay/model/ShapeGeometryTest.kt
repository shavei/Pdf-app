package com.pdfapp.overlay.model

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import org.junit.Test
import kotlin.math.abs

class ShapeGeometryTest {
    @Test
    fun `barbs of a horizontal arrow point back toward the start and straddle the shaft`() {
        val start = PdfPoint(0f, 0f)
        val end = PdfPoint(100f, 0f)
        val (barb1, barb2) = ShapeGeometry.arrowHeadBarbs(start, end)

        // Both barbs sit behind the tip (toward the start) …
        assertThat(barb1.x).isLessThan(end.x)
        assertThat(barb2.x).isLessThan(end.x)
        // … and are mirror images across the horizontal shaft.
        assertThat(barb1.y).isWithin(TOLERANCE).of(-barb2.y)
        assertThat(abs(barb1.y)).isGreaterThan(0f)
    }

    @Test
    fun `barb length is capped for a very long shaft`() {
        val start = PdfPoint(0f, 0f)
        val end = PdfPoint(1000f, 0f)
        val (barb1, _) = ShapeGeometry.arrowHeadBarbs(start, end)
        // The tip-to-barb distance may not exceed the 24pt cap.
        val distance = kotlin.math.hypot(barb1.x - end.x, barb1.y - end.y)
        assertThat(distance).isAtMost(24f + TOLERANCE)
    }

    @Test
    fun `a zero-length shaft yields no arrowhead`() {
        val point = PdfPoint(5f, 5f)
        val (barb1, barb2) = ShapeGeometry.arrowHeadBarbs(point, point)
        assertThat(barb1).isEqualTo(point)
        assertThat(barb2).isEqualTo(point)
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
