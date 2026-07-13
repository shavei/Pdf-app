package com.pdfapp.overlay

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Interprets zoom/pan touch input for a view hosting a [ViewportTransform]:
 * two fingers always pinch-zoom and pan; a single finger pans only when the
 * host marks the gesture as pannable at `ACTION_DOWN` (so drawing and dragging
 * keep priority).
 *
 * Feed every [MotionEvent] through [onTouchEvent]; a `true` result means the
 * event belonged to a viewport gesture and must not reach the drawing/editing
 * handlers. Once a pinch starts the rest of the gesture is consumed, and
 * [onTransformStarted] tells the host to abandon any half-started stroke/drag.
 */
internal class ViewportGestureController(
    private val viewport: ViewportTransform,
    private val onTransformStarted: () -> Unit,
    private val onViewportChanged: () -> Unit,
) {
    private var transforming = false
    private var panning = false
    private var panCandidate = false
    private var downX = 0f
    private var downY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastSpan = 0f

    /**
     * @param allowSinglePointerPan whether a one-finger drag may pan the page
     *   for the gesture beginning with this `ACTION_DOWN` (ignored otherwise).
     */
    fun onTouchEvent(
        event: MotionEvent,
        allowSinglePointerPan: Boolean,
    ): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                transforming = false
                panning = false
                panCandidate = allowSinglePointerPan
                downX = event.x
                downY = event.y
                lastFocusX = event.x
                lastFocusY = event.y
                // The mode handler still sees the press; we only observe it.
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!transforming) {
                    transforming = true
                    panning = false
                    onTransformStarted()
                }
                updateBaseline(event, ignoreIndex = -1)
                true
            }
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_POINTER_UP -> {
                if (transforming) updateBaseline(event, ignoreIndex = event.actionIndex)
                transforming
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = transforming || panning
                transforming = false
                panning = false
                panCandidate = false
                consumed
            }
            else -> false
        }

    private fun handleMove(event: MotionEvent): Boolean {
        if (transforming) {
            applyPinch(event)
            return true
        }
        if (!panCandidate) return false
        if (!panning && hypot(event.x - downX, event.y - downY) >= PAN_SLOP_PX) panning = true
        if (panning) {
            viewport.panBy(event.x - lastFocusX, event.y - lastFocusY)
            onViewportChanged()
        }
        lastFocusX = event.x
        lastFocusY = event.y
        return panning
    }

    private fun applyPinch(event: MotionEvent) {
        val focusX = focus(event, ignoreIndex = -1) { i -> event.getX(i) }
        val focusY = focus(event, ignoreIndex = -1) { i -> event.getY(i) }
        val span = span(event, ignoreIndex = -1)
        val scaleFactor = if (lastSpan > 0f && span > 0f) span / lastSpan else 1f
        viewport.pinch(
            pivotX = lastFocusX,
            pivotY = lastFocusY,
            scaleFactor = scaleFactor,
            deltaX = focusX - lastFocusX,
            deltaY = focusY - lastFocusY,
        )
        lastFocusX = focusX
        lastFocusY = focusY
        lastSpan = span
        onViewportChanged()
    }

    /** Re-anchor after pointers change so the next move produces no jump. */
    private fun updateBaseline(
        event: MotionEvent,
        ignoreIndex: Int,
    ) {
        lastFocusX = focus(event, ignoreIndex) { i -> event.getX(i) }
        lastFocusY = focus(event, ignoreIndex) { i -> event.getY(i) }
        lastSpan = span(event, ignoreIndex)
    }

    /** Average pointer position along one axis, skipping a departing pointer. */
    private inline fun focus(
        event: MotionEvent,
        ignoreIndex: Int,
        axis: (Int) -> Float,
    ): Float {
        var sum = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == ignoreIndex) continue
            sum += axis(i)
            count++
        }
        return if (count == 0) 0f else sum / count
    }

    /** Distance between the first two remaining pointers, or 0 if fewer than two. */
    private fun span(
        event: MotionEvent,
        ignoreIndex: Int,
    ): Float {
        val indices = (0 until event.pointerCount).filter { it != ignoreIndex }
        if (indices.size < 2) return 0f
        val (a, b) = indices
        return hypot(event.getX(a) - event.getX(b), event.getY(a) - event.getY(b))
    }

    private companion object {
        // Movement below this (view pixels) is still a tap, not a pan.
        const val PAN_SLOP_PX = 16f
    }
}
