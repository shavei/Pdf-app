package com.pdfapp.overlay.model

/**
 * The complete, ordered set of overlays for one page. Immutable: mutating
 * operations return a new layer so the view/viewmodel can treat it as state.
 */
data class OverlayLayer(
    val pageIndex: Int,
    val texts: List<TextOverlay> = emptyList(),
    val signatures: List<InkSignature> = emptyList(),
) {
    /** True when the page has no overlays to flatten. */
    val isEmpty: Boolean get() = texts.isEmpty() && signatures.all { it.isEmpty }

    fun withText(text: TextOverlay): OverlayLayer = copy(texts = texts + text)

    fun withSignature(signature: InkSignature): OverlayLayer = copy(signatures = signatures + signature)

    fun removeText(id: String): OverlayLayer = copy(texts = texts.filterNot { it.id == id })

    fun removeSignature(id: String): OverlayLayer = copy(signatures = signatures.filterNot { it.id == id })
}
