package com.pdfapp.ui.reader

/**
 * Pure zoom-target math for the continuous reader, kept free of Compose so the
 * double-tap toggle (mobile-ui-plan Phase D.1) is unit-testable on the JVM —
 * like [ReaderChrome] and [ReaderBack].
 */
object ReaderZoom {
    /** Fit-width — the resting zoom the document opens at. */
    const val FIT_WIDTH = 1f

    /** Comfortable reading magnification a double-tap jumps to from fit-width. */
    const val DOUBLE_TAP_ZOOM = 2.5f

    /** Zoom at (or below) this is treated as "fit width" for toggle purposes. */
    const val FIT_ZOOM_EPSILON = 1.001f

    /**
     * Where a double-tap should land: zoomed in past fit-width toggles back to
     * fit, and at (or below) fit-width it jumps to the comfortable reading zoom.
     * So double-tapping repeatedly ping-pongs between [FIT_WIDTH] and
     * [DOUBLE_TAP_ZOOM] no matter what pinch zoom the user was left on.
     */
    fun doubleTapTarget(currentZoom: Float): Float = if (currentZoom > FIT_ZOOM_EPSILON) FIT_WIDTH else DOUBLE_TAP_ZOOM
}
