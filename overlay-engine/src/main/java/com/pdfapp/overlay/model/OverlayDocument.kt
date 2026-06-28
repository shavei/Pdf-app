package com.pdfapp.overlay.model

/**
 * All overlays for a multi-page document, keyed by page index. Immutable: every
 * edit returns a new document so the view-model can treat it as state.
 *
 * Pages without any overlays are simply absent from [layers]; [layerFor] returns
 * a fresh empty layer for them so callers never deal with nulls.
 */
data class OverlayDocument(
    val pageCount: Int,
    private val layers: Map<Int, OverlayLayer> = emptyMap(),
) {
    init {
        require(pageCount > 0) { "pageCount must be positive: $pageCount" }
    }

    /** The (possibly empty) overlay layer for [pageIndex]. */
    fun layerFor(pageIndex: Int): OverlayLayer {
        require(pageIndex in 0 until pageCount) {
            "pageIndex $pageIndex out of bounds (0..${pageCount - 1})"
        }
        return layers[pageIndex] ?: OverlayLayer(pageIndex)
    }

    /** Return a copy with [layer] stored for its page index. */
    fun withLayer(layer: OverlayLayer): OverlayDocument {
        require(layer.pageIndex in 0 until pageCount) {
            "pageIndex ${layer.pageIndex} out of bounds (0..${pageCount - 1})"
        }
        return copy(layers = layers + (layer.pageIndex to layer))
    }

    /** Layers that actually have something to flatten, in page order. */
    val nonEmptyLayers: List<OverlayLayer>
        get() = (0 until pageCount).map { layerFor(it) }.filterNot { it.isEmpty }

    /** True when at least one page has overlays to save. */
    val hasOverlays: Boolean get() = layers.values.any { !it.isEmpty }
}
