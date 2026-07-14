package com.pdfapp.overlay

/**
 * Mutable mapping between view (screen) pixels and content (rendered page
 * bitmap) pixels for a zoomable, pannable page.
 *
 * The content is drawn at [scale] — the fit-to-view scale times the user
 * [zoom] — translated by ([offsetX], [offsetY]) view pixels. Offsets are
 * always clamped so the page stays centred while smaller than the view and
 * never pans fully off-screen once zoomed in.
 *
 * Until both the view and content sizes are known the transform is identity,
 * so callers can map coordinates unconditionally.
 *
 * Dependency-free (no Android types) so the zoom/pan math can be exhaustively
 * unit tested on the JVM.
 */
class ViewportTransform {
    /** User zoom factor on top of the fit scale; 1 shows the whole page. */
    var zoom: Float = 1f
        private set

    /** View-space x of the content origin. */
    var offsetX: Float = 0f
        private set

    /** View-space y of the content origin. */
    var offsetY: Float = 0f
        private set

    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var contentWidth: Float = 0f
    private var contentHeight: Float = 0f

    private val sized: Boolean
        get() = viewWidth > 0f && viewHeight > 0f && contentWidth > 0f && contentHeight > 0f

    /** Scale so the whole content fits (and fills one axis of) the view. */
    val fitScale: Float
        get() = if (sized) minOf(viewWidth / contentWidth, viewHeight / contentHeight) else 1f

    /** Total content-to-view scale factor. */
    val scale: Float
        get() = fitScale * zoom

    /** Record the view size (e.g. from `onSizeChanged`), keeping zoom/pan valid. */
    fun setViewSize(
        width: Float,
        height: Float,
    ) {
        viewWidth = width
        viewHeight = height
        clampOffsets()
    }

    /** Bind new content (a freshly rendered page), resetting zoom and pan. */
    fun setContentSize(
        width: Float,
        height: Float,
    ) {
        contentWidth = width
        contentHeight = height
        reset()
    }

    /** Reset to fully zoomed-out and centred. */
    fun reset() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        clampOffsets()
    }

    /**
     * Zoom to [target] (clamped to the allowed range) about the view centre,
     * keeping the content currently under the centre fixed. Used for the
     * reader's fit-width / fit-page presets.
     */
    fun setZoom(target: Float) {
        pinch(viewWidth / 2f, viewHeight / 2f, scaleFactor = target / zoom, deltaX = 0f, deltaY = 0f)
    }

    /** Map a view-space x coordinate into content (bitmap) space. */
    fun toContentX(viewX: Float): Float = (viewX - offsetX) / scale

    /** Map a view-space y coordinate into content (bitmap) space. */
    fun toContentY(viewY: Float): Float = (viewY - offsetY) / scale

    /** Translate the content by a view-space delta, clamped to bounds. */
    fun panBy(
        deltaX: Float,
        deltaY: Float,
    ) {
        offsetX += deltaX
        offsetY += deltaY
        clampOffsets()
    }

    /**
     * Apply one step of a pinch gesture: scale by [scaleFactor] about the
     * view-space pivot ([pivotX], [pivotY]) — keeping the content under the
     * pivot stationary — then translate by ([deltaX], [deltaY]).
     */
    fun pinch(
        pivotX: Float,
        pivotY: Float,
        scaleFactor: Float,
        deltaX: Float,
        deltaY: Float,
    ) {
        val newZoom = (zoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        zoom = newZoom
        offsetX = pivotX - (pivotX - offsetX) * applied + deltaX
        offsetY = pivotY - (pivotY - offsetY) * applied + deltaY
        clampOffsets()
    }

    private fun clampOffsets() {
        if (!sized) return
        offsetX = clampAxis(offsetX, contentWidth * scale, viewWidth)
        offsetY = clampAxis(offsetY, contentHeight * scale, viewHeight)
    }

    /** Centre content smaller than the view; otherwise keep the view covered. */
    private fun clampAxis(
        offset: Float,
        scaledContent: Float,
        view: Float,
    ): Float =
        if (scaledContent <= view) {
            (view - scaledContent) / 2f
        } else {
            offset.coerceIn(view - scaledContent, 0f)
        }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 8f
    }
}
