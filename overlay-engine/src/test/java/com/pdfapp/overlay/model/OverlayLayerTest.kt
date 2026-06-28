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
}
