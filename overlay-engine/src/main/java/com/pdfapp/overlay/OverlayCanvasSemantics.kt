package com.pdfapp.overlay

import com.pdfapp.overlay.model.OverlayLayer

/**
 * What a screen reader is told about the edit canvas (mobile-ui-plan Phase F.2).
 *
 * [OverlayCanvasView] is a drawing surface, so there is nothing for TalkBack to
 * infer from its contents: without this it announces an unlabelled view. The
 * description names the page being edited, the tool that touch input will use,
 * and what is already on the page — the state a sighted user reads off the
 * canvas at a glance.
 *
 * Pure so the wording is unit-testable without inflating a View.
 */
object OverlayCanvasSemantics {
    fun describe(
        pageIndex: Int,
        mode: OverlayCanvasView.Mode,
        layer: OverlayLayer,
    ): String {
        val tool =
            when (mode) {
                OverlayCanvasView.Mode.INK -> "sign tool"
                OverlayCanvasView.Mode.TEXT -> "text tool"
                OverlayCanvasView.Mode.EDIT -> "select and move tool"
            }
        return "Edit page ${pageIndex + 1}, $tool. ${contents(layer)}"
    }

    private fun contents(layer: OverlayLayer): String {
        val parts = mutableListOf<String>()
        if (layer.texts.isNotEmpty()) parts += count(layer.texts.size, "text overlay", "text overlays")
        val signatures = layer.signatures.count { !it.isEmpty }
        if (signatures > 0) parts += count(signatures, "signature", "signatures")
        return if (parts.isEmpty()) "No overlays on this page" else parts.joinToString(", ")
    }

    private fun count(
        n: Int,
        singular: String,
        plural: String,
    ): String = "$n ${if (n == 1) singular else plural}"
}
