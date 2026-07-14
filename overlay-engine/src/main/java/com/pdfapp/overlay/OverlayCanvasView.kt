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
import com.pdfapp.overlay.model.Shape
import com.pdfapp.overlay.model.ShapeGeometry
import com.pdfapp.overlay.model.ShapeKind
import com.pdfapp.overlay.model.TextOverlay

/**
 * Interactive view that paints a rendered PDF page plus its overlays and
 * captures input for the two overlay kinds.
 *
 * The view fills whatever space it is given and letterboxes the page inside it
 * via a [ViewportTransform]: two fingers pinch-zoom and pan in every mode, and
 * a single finger pans wherever it would not draw or drag. All touch input is
 * mapped from view space into page-bitmap space before use, so zoom never
 * affects where overlays land.
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
        enum class Mode { INK, TEXT, EDIT, SHAPE }

        var mode: Mode = Mode.INK
            set(value) {
                if (field != value) {
                    field = value
                    // Leaving edit mode clears any selection highlight, and
                    // switching tools abandons a half-drawn shape.
                    selectedTextId = null
                    draggingText = null
                    activeShape = null
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

        /** Shape kind drawn in [Mode.SHAPE]. */
        var shapeKind: ShapeKind = ShapeKind.RECTANGLE

        /** Stroke colour used for new shapes. */
        var shapeColorArgb: Int = Shape.DEFAULT_COLOR

        /** Stroke width (PDF points) used for new shapes. */
        var shapeStrokeWidthPt: Float = Shape.DEFAULT_STROKE_WIDTH_PT

        /** Current overlays. Assigning triggers a redraw and notifies [onLayerChanged]. */
        var layer: OverlayLayer = OverlayLayer(pageIndex = 0)
            set(value) {
                field = value
                onLayerChanged?.invoke(value)
                invalidate()
            }

        /** Current pinch-zoom factor; 1 shows the whole page. Read-only for hosts/tests. */
        val zoom: Float get() = viewport.zoom

        private var page: RenderedPage? = null

        private val viewport = ViewportTransform()
        private val viewportGestures =
            ViewportGestureController(
                viewport = viewport,
                onTransformStarted = ::abandonGestureForTransform,
                onViewportChanged = ::invalidate,
            )

        // Strokes captured for the signature currently being drawn, in pixel space.
        private val activeStrokes = mutableListOf<MutableList<PixelPoint>>()

        // Whether the current gesture has an ink stroke in progress (so a pinch
        // that starts mid-stroke can discard the accidental ink).
        private var inkStrokeActive = false

        // Shape currently being dragged out, in pixel space; null between gestures.
        private var activeShape: PixelShape? = null

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

        private val shapePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Shape.DEFAULT_COLOR
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
            inkStrokeActive = false
            activeShape = null
            selectedTextId = null
            draggingText = null
            viewport.setContentSize(rendered.bitmap.width.toFloat(), rendered.bitmap.height.toFloat())
            layer = initialLayer
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
            inkStrokeActive = false
            invalidate()
            return signature
        }

        /**
         * Undo the most recent action: an in-progress stroke first, then the last
         * committed shape, signature, or text overlay in that order.
         */
        fun undo() {
            if (activeStrokes.isNotEmpty()) {
                activeStrokes.removeAt(activeStrokes.lastIndex)
                invalidate()
                return
            }
            val shapes = layer.shapes
            val signatures = layer.signatures
            val texts = layer.texts
            layer =
                when {
                    shapes.isNotEmpty() -> layer.removeShape(shapes.last().id)
                    signatures.isNotEmpty() -> layer.removeSignature(signatures.last().id)
                    texts.isNotEmpty() -> layer.removeText(texts.last().id)
                    else -> return
                }
        }

        /** Remove all overlays and in-progress strokes/shapes from the current page. */
        fun clearOverlays() {
            activeStrokes.clear()
            inkStrokeActive = false
            activeShape = null
            layer = OverlayLayer(pageIndex = page?.index ?: layer.pageIndex)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            viewport.setViewSize(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rendered = page ?: return
            val checkpoint = canvas.save()
            canvas.translate(viewport.offsetX, viewport.offsetY)
            canvas.scale(viewport.scale, viewport.scale)
            canvas.drawBitmap(rendered.bitmap, 0f, 0f, null)
            drawCommittedShapes(canvas)
            drawActiveShape(canvas)
            drawCommittedSignatures(canvas)
            drawActiveStrokes(canvas)
            drawTexts(canvas)
            drawSelection(canvas)
            canvas.restoreToCount(checkpoint)
        }

        private fun drawCommittedShapes(canvas: Canvas) {
            val mapper = page?.mapper ?: return
            layer.shapes.forEach { shape ->
                shapePaint.color = shape.strokeColorArgb
                shapePaint.strokeWidth = shape.strokeWidthPt * mapper.pixelsPerPoint
                val start = mapper.toPixel(shape.start)
                val end = mapper.toPixel(shape.end)
                drawShapePath(canvas, shape.kind, start, end)
            }
        }

        private fun drawActiveShape(canvas: Canvas) {
            val shape = activeShape ?: return
            shapePaint.color = shapeColorArgb
            shapePaint.strokeWidth = shapeStrokeWidthPt * (page?.mapper?.pixelsPerPoint ?: 1f)
            drawShapePath(canvas, shape.kind, shape.start, shape.end)
        }

        /** Paint one shape's outline given its two anchor points in pixel space. */
        private fun drawShapePath(
            canvas: Canvas,
            kind: ShapeKind,
            start: PixelPoint,
            end: PixelPoint,
        ) {
            when (kind) {
                ShapeKind.RECTANGLE ->
                    canvas.drawRect(
                        minOf(start.x, end.x),
                        minOf(start.y, end.y),
                        maxOf(start.x, end.x),
                        maxOf(start.y, end.y),
                        shapePaint,
                    )
                ShapeKind.ELLIPSE ->
                    canvas.drawOval(
                        minOf(start.x, end.x),
                        minOf(start.y, end.y),
                        maxOf(start.x, end.x),
                        maxOf(start.y, end.y),
                        shapePaint,
                    )
                ShapeKind.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, shapePaint)
                ShapeKind.ARROW -> {
                    canvas.drawLine(start.x, start.y, end.x, end.y, shapePaint)
                    // Barbs are computed in PDF points, so map them into pixels.
                    val mapper = page?.mapper ?: return
                    val (b1, b2) =
                        ShapeGeometry.arrowHeadBarbs(mapper.toPdfPoint(start), mapper.toPdfPoint(end))
                    val p1 = mapper.toPixel(b1)
                    val p2 = mapper.toPixel(b2)
                    canvas.drawLine(end.x, end.y, p1.x, p1.y, shapePaint)
                    canvas.drawLine(end.x, end.y, p2.x, p2.y, shapePaint)
                }
            }
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
            // Zoom/pan gestures take priority: a single finger may pan only
            // where it would not draw or drag, two fingers always transform.
            if (viewportGestures.onTouchEvent(event, allowSinglePointerPan(event, mapper))) {
                if (isGestureEnd(event)) parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            updateParentInterception(event, mapper)
            return when (mode) {
                Mode.TEXT -> handleTextTouch(event, mapper)
                Mode.INK -> handleInkTouch(event)
                Mode.EDIT -> handleEditTouch(event, mapper)
                Mode.SHAPE -> handleShapeTouch(event, mapper)
            }
        }

        private fun allowSinglePointerPan(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ): Boolean =
            event.actionMasked == MotionEvent.ACTION_DOWN &&
                when (mode) {
                    Mode.INK, Mode.SHAPE -> false
                    Mode.TEXT -> true
                    Mode.EDIT -> textAt(contentPoint(event.x, event.y), mapper) == null
                }

        /**
         * Scrollable ancestors steal the gesture once it passes their touch
         * slop, cancelling ink strokes and text drags after a few pixels. Claim
         * gestures that draw or drag; taps and misses stay interceptable.
         */
        private fun updateParentInterception(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val consumesGesture =
                        when (mode) {
                            Mode.INK, Mode.SHAPE -> true
                            Mode.EDIT -> textAt(contentPoint(event.x, event.y), mapper) != null
                            Mode.TEXT -> false
                        }
                    if (consumesGesture) parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        private fun isGestureEnd(event: MotionEvent): Boolean =
            event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL

        /** Map a view-space touch position into page-bitmap space. */
        private fun contentPoint(
            viewX: Float,
            viewY: Float,
        ): PixelPoint = PixelPoint(viewport.toContentX(viewX), viewport.toContentY(viewY))

        /**
         * A pinch started mid-gesture: discard the accidental ink begun by the
         * first finger and stop any text drag where it stands.
         */
        private fun abandonGestureForTransform() {
            if (inkStrokeActive && activeStrokes.isNotEmpty()) {
                activeStrokes.removeAt(activeStrokes.lastIndex)
            }
            inkStrokeActive = false
            activeShape = null
            draggingText = null
            parent?.requestDisallowInterceptTouchEvent(true)
            invalidate()
        }

        private fun handleTextTouch(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                onTextPlacementRequested?.invoke(mapper.toPdfPoint(contentPoint(event.x, event.y)))
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
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val touch = contentPoint(event.x, event.y)
                    val hit = textAt(touch, mapper)
                    selectedTextId = hit?.id
                    draggingText = hit
                    dragMoved = false
                    if (hit != null) {
                        val anchor = mapper.toPixel(hit.position)
                        dragGrabOffset = PixelPoint(touch.x - anchor.x, touch.y - anchor.y)
                        dragStart = touch
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragging = draggingText
                    val touch = contentPoint(event.x, event.y)
                    val passedSlop =
                        kotlin.math.hypot(touch.x - dragStart.x, touch.y - dragStart.y) >= TAP_SLOP_PX
                    if (dragging != null && (dragMoved || passedSlop)) {
                        dragMoved = true
                        val newAnchor = PixelPoint(touch.x - dragGrabOffset.x, touch.y - dragGrabOffset.y)
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
                MotionEvent.ACTION_CANCEL -> {
                    draggingText = null
                    invalidate()
                }
                else -> return false
            }
            return true
        }

        /** Topmost text overlay whose padded bounds contain [pixel], or null. */
        private fun textAt(
            pixel: PixelPoint,
            mapper: CoordinateMapper,
        ): TextOverlay? = layer.texts.lastOrNull { textBounds(it, mapper).contains(pixel.x, pixel.y) }

        private fun handleInkTouch(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeStrokes.add(mutableListOf(contentPoint(event.x, event.y)))
                    inkStrokeActive = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = activeStrokes.lastOrNull() ?: return true
                    // Batched samples between frames carry the fine curvature of a
                    // signature; taking only the latest position produces jagged ink.
                    for (i in 0 until event.historySize) {
                        stroke.add(contentPoint(event.getHistoricalX(i), event.getHistoricalY(i)))
                    }
                    stroke.add(contentPoint(event.x, event.y))
                }
                MotionEvent.ACTION_UP -> {
                    activeStrokes.lastOrNull()?.add(contentPoint(event.x, event.y))
                    inkStrokeActive = false
                    performClick()
                }
                // Keep whatever was inked before the system cancelled the gesture;
                // undo can discard it if unwanted.
                MotionEvent.ACTION_CANCEL -> inkStrokeActive = false
                else -> return false
            }
            invalidate()
            return true
        }

        /**
         * Drag out a shape: the down point anchors [Shape.start], each move updates
         * [Shape.end] for a live preview, and lift-off commits it into the layer in
         * PDF points. A shape that never left its start point is discarded.
         */
        private fun handleShapeTouch(
            event: MotionEvent,
            mapper: CoordinateMapper,
        ): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val touch = contentPoint(event.x, event.y)
                    activeShape = PixelShape(shapeKind, touch, touch)
                }
                MotionEvent.ACTION_MOVE -> {
                    val shape = activeShape ?: return true
                    activeShape = shape.copy(end = contentPoint(event.x, event.y))
                }
                MotionEvent.ACTION_UP -> {
                    activeShape?.let { commitShape(it, contentPoint(event.x, event.y), mapper) }
                    activeShape = null
                    performClick()
                }
                MotionEvent.ACTION_CANCEL -> activeShape = null
                else -> return false
            }
            invalidate()
            return true
        }

        /** Turn the dragged-out [pixelShape] into a committed [Shape] unless it is degenerate. */
        private fun commitShape(
            pixelShape: PixelShape,
            releasedAt: PixelPoint,
            mapper: CoordinateMapper,
        ) {
            val start = mapper.toPdfPoint(pixelShape.start)
            val end = mapper.toPdfPoint(releasedAt)
            val shape =
                Shape(
                    kind = pixelShape.kind,
                    start = start,
                    end = end,
                    strokeWidthPt = shapeStrokeWidthPt,
                    strokeColorArgb = shapeColorArgb,
                )
            if (shape.isEmpty) return
            layer = layer.withShape(shape)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        /** In-progress shape held in pixel space while the finger drags it out. */
        private data class PixelShape(
            val kind: ShapeKind,
            val start: PixelPoint,
            val end: PixelPoint,
        )

        private companion object {
            // Touch/highlight padding around a glyph box, in bitmap pixels.
            const val TOUCH_PADDING_PX = 24f

            // Movement under this distance (pixels) counts as a tap, not a drag.
            const val TAP_SLOP_PX = 16f

            const val SELECTION_STROKE_PX = 2f
            const val SELECTION_COLOR = 0xFF1A73E8.toInt()
        }
    }
