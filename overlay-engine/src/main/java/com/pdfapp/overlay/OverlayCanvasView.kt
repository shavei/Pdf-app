package com.pdfapp.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pdfapp.core.renderer.RenderedPage
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
class OverlayCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class Mode { INK, TEXT }

    var mode: Mode = Mode.INK

    /** Invoked when the user taps in [Mode.TEXT]; host shows a text-entry dialog. */
    var onTextPlacementRequested: ((PdfPoint) -> Unit)? = null

    /** Current overlays. Assigning triggers a redraw. */
    var layer: OverlayLayer = OverlayLayer(pageIndex = 0)
        set(value) {
            field = value
            invalidate()
        }

    private var page: RenderedPage? = null

    // Strokes captured for the signature currently being drawn, in pixel space.
    private val activeStrokes = mutableListOf<MutableList<PixelPoint>>()

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = InkSignature.DEFAULT_COLOR
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TextOverlay.DEFAULT_COLOR
    }

    /** Bind the page to display and reset any in-progress drawing. */
    fun setPage(rendered: RenderedPage) {
        page = rendered
        layer = OverlayLayer(pageIndex = rendered.index)
        activeStrokes.clear()
        requestLayout()
        invalidate()
    }

    /** Collect the in-progress strokes into an [InkSignature] in PDF points. */
    fun commitSignature(): InkSignature? {
        val mapper = page?.mapper ?: return null
        val strokes = activeStrokes
            .filter { it.isNotEmpty() }
            .map { stroke -> stroke.map { mapper.toPdfPoint(it) } }
        if (strokes.isEmpty()) return null
        val signature = InkSignature(strokes = strokes)
        layer = layer.withSignature(signature)
        activeStrokes.clear()
        invalidate()
        return signature
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
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
        inkPaint.color = InkSignature.DEFAULT_COLOR
        inkPaint.strokeWidth = InkSignature.DEFAULT_STROKE_WIDTH_PT * mapper.pixelsPerPoint
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val mapper = page?.mapper ?: return false
        return when (mode) {
            Mode.TEXT -> handleTextTouch(event, mapper)
            Mode.INK -> handleInkTouch(event)
        }
    }

    private fun handleTextTouch(event: MotionEvent, mapper: com.pdfapp.core.renderer.model.CoordinateMapper): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onTextPlacementRequested?.invoke(mapper.toPdfPoint(PixelPoint(event.x, event.y)))
            performClick()
            return true
        }
        return true
    }

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
}
