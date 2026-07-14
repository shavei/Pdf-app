package com.pdfapp.overlay.model

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import org.junit.Test

class OverlayLayerTest {
    @Test
    fun `new layer is empty`() {
        assertThat(OverlayLayer(pageIndex = 0).isEmpty).isTrue()
    }

    @Test
    fun `withText adds a text overlay immutably`() {
        val base = OverlayLayer(pageIndex = 1)
        val updated = base.withText(TextOverlay("Hello", PdfPoint(10f, 20f)))
        assertThat(base.texts).isEmpty()
        assertThat(updated.texts).hasSize(1)
        assertThat(updated.isEmpty).isFalse()
    }

    @Test
    fun `withSignature adds a signature`() {
        val sig = InkSignature(strokes = listOf(listOf(PdfPoint(0f, 0f), PdfPoint(5f, 5f))))
        val layer = OverlayLayer(pageIndex = 0).withSignature(sig)
        assertThat(layer.signatures).containsExactly(sig)
        assertThat(layer.isEmpty).isFalse()
    }

    @Test
    fun `layer with only an empty signature is still empty`() {
        val emptySig = InkSignature(strokes = listOf(emptyList()))
        val layer = OverlayLayer(pageIndex = 0).withSignature(emptySig)
        assertThat(layer.isEmpty).isTrue()
    }

    @Test
    fun `removeText drops the matching overlay by id`() {
        val text = TextOverlay("Bye", PdfPoint(1f, 2f))
        val layer = OverlayLayer(pageIndex = 0).withText(text).removeText(text.id)
        assertThat(layer.texts).isEmpty()
    }

    @Test
    fun `updateText replaces content and position in place`() {
        val original = TextOverlay("Draft", PdfPoint(10f, 20f))
        val other = TextOverlay("Keep", PdfPoint(0f, 0f))
        val layer = OverlayLayer(pageIndex = 0).withText(original).withText(other)

        val moved = original.copy(text = "Final", position = PdfPoint(30f, 40f))
        val updated = layer.updateText(moved)

        assertThat(updated.texts).hasSize(2)
        val edited = updated.texts.first { it.id == original.id }
        assertThat(edited.text).isEqualTo("Final")
        assertThat(edited.position).isEqualTo(PdfPoint(30f, 40f))
        // Order and the untouched overlay are preserved.
        assertThat(updated.texts.map { it.id }).containsExactly(original.id, other.id).inOrder()
    }

    @Test
    fun `updateText leaves the layer unchanged when the id is unknown`() {
        val text = TextOverlay("Hello", PdfPoint(1f, 2f))
        val layer = OverlayLayer(pageIndex = 0).withText(text)
        val stray = TextOverlay("Ghost", PdfPoint(9f, 9f))
        assertThat(layer.updateText(stray).texts).containsExactly(text)
    }

    @Test
    fun `withShape adds a shape immutably`() {
        val base = OverlayLayer(pageIndex = 2)
        val shape = Shape(ShapeKind.RECTANGLE, PdfPoint(0f, 0f), PdfPoint(10f, 10f))
        val updated = base.withShape(shape)
        assertThat(base.shapes).isEmpty()
        assertThat(updated.shapes).containsExactly(shape)
        assertThat(updated.isEmpty).isFalse()
    }

    @Test
    fun `removeShape drops the matching shape by id`() {
        val shape = Shape(ShapeKind.LINE, PdfPoint(0f, 0f), PdfPoint(5f, 5f))
        val layer = OverlayLayer(pageIndex = 0).withShape(shape).removeShape(shape.id)
        assertThat(layer.shapes).isEmpty()
    }

    @Test
    fun `layer with only a degenerate shape is still empty`() {
        val degenerate = Shape(ShapeKind.ELLIPSE, PdfPoint(3f, 3f), PdfPoint(3f, 3f))
        assertThat(OverlayLayer(pageIndex = 0).withShape(degenerate).isEmpty).isTrue()
    }
}
