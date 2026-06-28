package com.pdfapp.overlay.model

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import org.junit.Test

class OverlayDocumentTest {
    private fun text(page: Int) = OverlayLayer(pageIndex = page).withText(TextOverlay("x", PdfPoint(1f, 2f)))

    @Test
    fun `layerFor returns an empty layer for an untouched page`() {
        val doc = OverlayDocument(pageCount = 3)
        assertThat(doc.layerFor(2).isEmpty).isTrue()
        assertThat(doc.layerFor(2).pageIndex).isEqualTo(2)
    }

    @Test
    fun `withLayer stores per page and is immutable`() {
        val base = OverlayDocument(pageCount = 3)
        val updated = base.withLayer(text(1))
        assertThat(base.hasOverlays).isFalse()
        assertThat(updated.hasOverlays).isTrue()
        assertThat(updated.layerFor(1).texts).hasSize(1)
        assertThat(updated.layerFor(0).isEmpty).isTrue()
    }

    @Test
    fun `nonEmptyLayers returns only pages with overlays in order`() {
        val doc =
            OverlayDocument(pageCount = 4)
                .withLayer(text(2))
                .withLayer(text(0))
        assertThat(doc.nonEmptyLayers.map { it.pageIndex }).containsExactly(0, 2).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layerFor rejects out-of-range page`() {
        OverlayDocument(pageCount = 2).layerFor(5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pageCount must be positive`() {
        OverlayDocument(pageCount = 0)
    }
}
