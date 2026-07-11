package com.pdfapp.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pdfapp.core.renderer.RenderedPage
import com.pdfapp.core.renderer.model.CoordinateMapper
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.core.renderer.model.PixelPoint
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay

/**
 * Interactive view that paints a rendered PDF page plus its overlays and
 * captures input for the two overlay kinds.
 *
 * Everything user-authored is converted to PDF points via the page's
 * [com.pdfapp.core.renderer.model.CoordinateMapper] immediately, so the layer
 * this view holds is exactly what `:file-persistence` flattens — no pixel
 * coordinates leak out of this class. The view performs no file I/O.
 */
class OverlayCanvasView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        enum class Mode { INK, TEXT, EDIT }

        var mode: Mode = Mode.INK
            set(value) {
                if (field != value) {
                    field = value
                    // Leaving edit mode clears any selection highlight.
                    selectedTextId = null
                    draggingText = null
                    invalidate()
                }
            }

        /** Invoked when the user taps in [Mode.TEXT]; host shows a text-entry dialog. */
        var onTextPlacementRequested: ((PdfPoint) -> Unit)? = null

        /** Invoked when the user taps an existing text in [Mode.EDIT]; host shows an edit dialog. */
        var onTextEditRequested: ((TextOverlay) -> Unit)? = null

        /** Invoked whenever [layer] changes, so the host can persist it per page. */
        var onLayerChanged: ((OverlayLayer) -> Unit)? = null

        /** Ink colour used for new strokes. */
        var inkColorArgb: Int = InkSignature.DEFAULT_COLOR

        /** Ink stroke width (PDF points) used for new strokes. */
        var inkStrokeWidthPt: Float = InkSignature.DEFAULT_STROKE_WIDTH_PT

        /** Current overlays. Assigning triggers a redraw and notifies [onLayerChanged]. */
        var layer: OverlayLayer = OverlayLayer(pageIndex = 0)
            set(value) {
                field = value
                onLayerChanged?.invoke(value)
                invalidate()
            }

        private var page: RenderedPage? = null

        // Strokes captured for the signature currently being drawn, in pixel space.
        private val activeStrokes = mutableListOf<MutableList<PixelPoint>>()

        // Edit-mode state: the text currently selected/dragged, plus grab bookkeeping.
        private var selectedTextId: String? = null
        private var draggingText: TextOverlay? = null
        private var dragGrabOffset = PixelPoint(0f, 0f)
        private var dragStart = PixelPoint(0f, 0f)
        private var dragMoved = false

        private val inkPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = InkSignature.DEFAULT_COLOR
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TextOverlay.DEFAULT_COLOR
            }

        private val selectionPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = SELECTION_STROKE_PX
                color = SELECTION_COLOR
            }

        /**
         * Bind the page to display along with its stored overlays, and reset any
         * in-progress drawing.
         */
        fun setPage(
            rendered: RenderedPage,
            initialLayer: OverlayLayer = OverlayLayer(pageIndex = rendered.index),
        ) {
            page = rendered
            activeStrokes.clear()
            selectedTextId = null
            draggingText = null
            layer = initialLayer
            requestLayout()
            invalidate()
        }

        /** Collect the in-progress strokes into an [InkSignature] in PDF points. */
        fun commitSignature(): InkSignature? {
            val mapper = page?.mapper ?: return null
            val strokes =
                activeStrokes
                    .filter { it.isNotEmpty() }
                    .map { stroke -> stroke.map { mapper.toPdfPoint(it) } }
            if (strokes.isEmpty()) return null
            val signature =
                InkSignature(
                    strokes = strokes,
                    strokeWidthPt = inkStrokeWidthPt,
                    colorArgb = inkColorArgb,
                )
            layer = layer.withSignature(signature)
            activeStrokes.clear()
            invalidate()
            return signature
        }

        /**
         * Undo the most recent action: an in-progress stroke first, otherwise the
         * last committed signature, otherwise the last text overlay.
         */
        fun undo() {
            if (activeStrokes.isNotEmpty()) {
                activeStrokes.removeAt(activeStrokes.lastIndex)
                invalidate()
                return
            }
            val signatures = layer.signatures
            val texts = layer.texts
            layer =
                when {
                    signatures.isNotEmpty() -> layer.removeSignature(signatures.last().id)
                    texts.isNotEmpty() -> layer.removeText(texts.last().id)
                    else -> return
                }
        }

        /** Remove all overlays and in-progress strokes from the current page. */
        fun clearOverlays() {
            activeStrokes.clear()
            layer = OverlayLayer(pageIndex = page?.index ?: layer.pageIndex)
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val rendered = page
            if (rendered == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            } else {
                setMeasuredDimension(rendered.bitmap.width, rendered.bitmap.height)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rendered = page ?: return
            canvas.drawBitmap(rendered.bitmap, 0f, 0f, null)
            drawCommittedSignatures(canvas)
            drawActiveStrokes(canvas)
            drawTexts(canvas)
            drawSelection(canvas)
        }

        private fun drawCommittedSignatures(canvas: Canvas) {
            val mapper = page?.mapper ?: return
            layer.signatures.forEach { signature ->
                inkPaint.color = signature.colorArgb
                inkPaint.strokeWidth = signature.strokeWidthPt * mapper.pixelsPerPoint
                signature.strokes.forEach { stroke ->
                    val path = Path()
                    stroke.forEachIndexed { i, point ->
                        val px = mapper.toPixel(point)
                        if (i == 0) path.moveTo(px.x, px.y) else path.lineTo(px.x, px.y)
                    }
                    canvas.drawPath(path, inkPaint)
                }
            }
        }

        private fun drawActiveStrokes(canvas: Canvas) {
            val mapper = page?.mapper ?: return
            inkPaint.color = inkColorArgb
            inkPaint.strokeWidth = inkStrokeWidthPt * mapper.pixelsPerPoint
            activeStrokes.forEach { stroke ->
                val path = Path()
                stroke.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, inkPaint)
            }
        }

        private fun drawTexts(canvas: Canvas) {
            val mapper = page?.mapper ?: return
            layer.texts.forEach { overlay ->
                textPaint.color = overlay.colorArgb
                textPaint.textSize = overlay.fontSizePt * mapper.pixelsPerPoint
                val px = mapper.toPixel(overlay.position)
                canvas.drawText(overlay.text, px.x, px.y, textPaint)
            }
        }

        /** Outline the selected text (only shown in [Mode.EDIT]) so the user sees the drag target. */
        private fun drawSelection(canvas: Canvas) {
            if (mode != Mode.EDIT) return
            val mapper = page?.mapper ?: return
            val selectedId = selectedTextId ?: return
            val overlay = layer.texts.firstOrNull { it.id == selectedId } ?: return
            val bounds = textBounds(overlay, mapper)
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, selectionPaint)
        }

        /** Pixel-space bounding box of [overlay]'s glyphs, padded for touch/highlight. */
        private fun textBounds(
            overlay: TextOverlay,
            mapper: CoordinateMapper,
        ): RectF {
            textPaint.textSize = overlay.fontSizePt * mapper.pixelsPerPoint
            val width = textPaint.measureText(overlay.text)
            val metrics = textPaint.fontMetrics
            val anchor = mapper.toPixel(overlay.position)
            return RectF(
                anchor.x - TOUCH_PADDING_PX,
                anchor.y + metrics.ascent - TOUCH_PADDING_PX,
                anchor.x + width + TOUCH_PADDING_PX,
                anchor.y + metrics.descent + TOUCH_PADDING_PX,
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val mapper = page?.mapper ?: return false
            return when (mode) {
                Mode.TEXT -> handleTextTouch(event, mapper)
                Mode.INK -> handleInkTouch(event)
                Mode.EDIT -> handleEditTouch(event, mapper)
            }
        }

        private fun handleTextTouch(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                onTextPlacementRequested?.invoke(mapper.toPdfPoint(PixelPoint(event.x, event.y)))
                performClick()
                return true
            }
            return true
        }

        /**
         * Select, drag-to-move, or tap-to-edit an existing text overlay. A press that
         * misses every glyph clears the selection; a press that hits one begins a drag,
         * which becomes a move once it passes [TAP_SLOP_PX] or an edit request otherwise.
         */
        private fun handleEditTouch(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = textAt(PixelPoint(event.x, event.y), mapper)
                    selectedTextId = hit?.id
                    draggingText = hit
                    dragMoved = false
                    if (hit != null) {
                        val anchor = mapper.toPixel(hit.position)
                        dragGrabOffset = PixelPoint(event.x - anchor.x, event.y - anchor.y)
                        dragStart = PixelPoint(event.x, event.y)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragging = draggingText
                    val passedSlop =
                        kotlin.math.hypot(event.x - dragStart.x, event.y - dragStart.y) >= TAP_SLOP_PX
                    if (dragging != null && (dragMoved || passedSlop)) {
                        dragMoved = true
                        val newAnchor = PixelPoint(event.x - dragGrabOffset.x, event.y - dragGrabOffset.y)
                        val updated = dragging.copy(position = mapper.toPdfPoint(newAnchor))
                        draggingText = updated
                        layer = layer.updateText(updated)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dragging = draggingText
                    draggingText = null
                    if (dragging != null && !dragMoved) onTextEditRequested?.invoke(dragging)
                    performClick()
                }
                else -> return false
            }
            return true
        }

        /** Topmost text overlay whose padded bounds contain [pixel], or null. */
        private fun textAt(
            pixel: PixelPoint,
            mapper: CoordinateMapper,
        ): TextOverlay? =
            layer.texts.lastOrNull { textBounds(it, mapper).contains(pixel.x, pixel.y) }

        private fun handleInkTouch(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> activeStrokes.add(mutableListOf(PixelPoint(event.x, event.y)))
                MotionEvent.ACTION_MOVE -> activeStrokes.lastOrNull()?.add(PixelPoint(event.x, event.y))
                MotionEvent.ACTION_UP -> {
                    activeStrokes.lastOrNull()?.add(PixelPoint(event.x, event.y))
                    performClick()
                }
                else -> return false
            }
            invalidate()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private companion object {
            // Touch/highlight padding around a glyph box, in bitmap pixels.
            const val TOUCH_PADDING_PX = 24f

            // Movement under this distance (pixels) counts as a tap, not a drag.
            const val TAP_SLOP_PX = 16f

            const val SELECTION_STROKE_PX = 2f
            const val SELECTION_COLOR = 0xFF1A73E8.toInt()
        }
    }
