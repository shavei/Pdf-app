package com.pdfapp.overlay.model

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import org.junit.Test

class ShapeTest {
    @Test
    fun `a shape with distinct anchors is not empty`() {
        val shape = Shape(ShapeKind.RECTANGLE, PdfPoint(10f, 10f), PdfPoint(40f, 60f))
        assertThat(shape.isEmpty).isFalse()
    }

    @Test
    fun `a shape whose anchors coincide is empty`() {
        val point = PdfPoint(25f, 25f)
        assertThat(Shape(ShapeKind.LINE, point, point).isEmpty).isTrue()
    }

    @Test
    fun `defaults match the ink defaults for width and use the shape colour`() {
        val shape = Shape(ShapeKind.ARROW, PdfPoint(0f, 0f), PdfPoint(1f, 1f))
        assertThat(shape.strokeWidthPt).isEqualTo(Shape.DEFAULT_STROKE_WIDTH_PT)
        assertThat(shape.strokeColorArgb).isEqualTo(Shape.DEFAULT_COLOR)
    }
}
