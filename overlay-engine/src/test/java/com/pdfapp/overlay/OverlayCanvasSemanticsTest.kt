package com.pdfapp.overlay

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import org.junit.Test

/**
 * Phase F.2 (mobile-ui-plan): the edit canvas is a drawing surface, so TalkBack
 * has nothing to infer from it — the description has to name the page, the tool
 * that touch input will use, and what is already on the page.
 */
class OverlayCanvasSemanticsTest {
    private fun text(x: Float) = TextOverlay(text = "signed", position = PdfPoint(x, 100f))

    private fun signature() = InkSignature(strokes = listOf(listOf(PdfPoint(0f, 0f), PdfPoint(10f, 10f))))

    @Test
    fun an_empty_page_names_the_page_and_the_active_tool() {
        assertThat(
            OverlayCanvasSemantics.describe(
                pageIndex = 2,
                mode = OverlayCanvasView.Mode.INK,
                layer = OverlayLayer(pageIndex = 2),
            ),
        ).isEqualTo("Edit page 3, sign tool. No overlays on this page")
    }

    @Test
    fun each_tool_is_named_the_way_its_toolbar_button_is() {
        fun describe(mode: OverlayCanvasView.Mode) =
            OverlayCanvasSemantics.describe(0, mode, OverlayLayer(pageIndex = 0))

        assertThat(describe(OverlayCanvasView.Mode.TEXT)).contains("text tool")
        assertThat(describe(OverlayCanvasView.Mode.EDIT)).contains("select and move tool")
    }

    @Test
    fun overlays_are_counted_and_pluralised() {
        val layer =
            OverlayLayer(
                pageIndex = 0,
                texts = listOf(text(10f), text(20f)),
                signatures = listOf(signature()),
            )
        assertThat(OverlayCanvasSemantics.describe(0, OverlayCanvasView.Mode.EDIT, layer))
            .isEqualTo("Edit page 1, select and move tool. 2 text overlays, 1 signature")
    }

    @Test
    fun an_empty_signature_is_not_announced_as_content() {
        // A signature with no strokes flattens to nothing, so it is not "on the
        // page" as far as the reader is concerned.
        val layer = OverlayLayer(pageIndex = 0, signatures = listOf(InkSignature(strokes = emptyList())))
        assertThat(OverlayCanvasSemantics.describe(0, OverlayCanvasView.Mode.INK, layer))
            .endsWith("No overlays on this page")
    }
}
