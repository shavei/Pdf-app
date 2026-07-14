package com.pdfapp.overlay

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.RenderedPage
import com.pdfapp.core.renderer.model.CoordinateMapper
import com.pdfapp.core.renderer.model.PageSize
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.ShapeKind
import com.pdfapp.overlay.model.TextOverlay
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression tests for gesture ownership and drag tracking. The editor hosts
 * the canvas inside scrollable containers, so unless the view claims a drawing
 * or dragging gesture via [android.view.ViewParent.requestDisallowInterceptTouchEvent]
 * the ancestor steals it at its touch slop and the stroke/drag dies after a
 * few pixels.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayCanvasViewTouchTest {
    private class RecordingParent(context: Context) : FrameLayout(context) {
        var disallowIntercept: Boolean = false

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            this.disallowIntercept = disallowIntercept
        }
    }

    private lateinit var parent: RecordingParent
    private lateinit var view: OverlayCanvasView

    // 200x200pt page rendered at 1px/pt keeps pixel and point magnitudes equal
    // (modulo the flipped y-axis), so expected positions stay readable.
    private val mapper = CoordinateMapper(PageSize(200f, 200f), pixelsPerPoint = 1f)

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        parent = RecordingParent(context)
        view = OverlayCanvasView(context)
        parent.addView(view)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        view.setPage(RenderedPage(index = 0, bitmap = bitmap, pageSize = mapper.pageSize, mapper = mapper))
    }

    private fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    /** Dispatch a multi-pointer event; [action] carries no pointer index yet. */
    private fun multiTouch(
        action: Int,
        points: List<Pair<Float, Float>>,
        actionIndex: Int = 0,
    ) {
        val properties =
            Array(points.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(points.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = points[index].first
                    y = points[index].second
                    pressure = 1f
                    size = 1f
                }
            }
        val event =
            MotionEvent.obtain(
                0L,
                0L,
                action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                points.size,
                properties,
                coords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        view.onTouchEvent(event)
        event.recycle()
    }

    /** Lay the view out so the viewport transform has a real size (fit scale 1). */
    private fun layoutView() = view.layout(0, 0, 200, 200)

    @Test
    fun `ink gesture claims touch stream and captures the full stroke`() {
        view.mode = OverlayCanvasView.Mode.INK

        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        assertThat(parent.disallowIntercept).isTrue()

        touch(MotionEvent.ACTION_MOVE, 60f, 60f)
        touch(MotionEvent.ACTION_UP, 100f, 100f)
        assertThat(parent.disallowIntercept).isFalse()

        val signature = view.commitSignature()
        assertThat(signature).isNotNull()
        val stroke = signature!!.strokes.single()
        assertThat(stroke).hasSize(3)
        assertThat(stroke.last()).isEqualTo(PdfPoint(100f, 100f))
    }

    @Test
    fun `text drag claims touch stream and moves the overlay the full distance`() {
        val text = TextOverlay(text = "Sign here", position = PdfPoint(50f, 150f))
        view.layer = OverlayLayer(pageIndex = 0).withText(text)
        view.mode = OverlayCanvasView.Mode.EDIT

        // Anchor pixel for PdfPoint(50, 150) on a 200pt page is (50, 50).
        touch(MotionEvent.ACTION_DOWN, 50f, 50f)
        assertThat(parent.disallowIntercept).isTrue()

        touch(MotionEvent.ACTION_MOVE, 120f, 90f)
        touch(MotionEvent.ACTION_UP, 120f, 90f)
        assertThat(parent.disallowIntercept).isFalse()

        val moved = view.layer.texts.single()
        assertThat(moved.position).isEqualTo(PdfPoint(120f, 110f))
    }

    @Test
    fun `press that misses every text leaves the gesture interceptable`() {
        val text = TextOverlay(text = "Sign here", position = PdfPoint(50f, 150f))
        view.layer = OverlayLayer(pageIndex = 0).withText(text)
        view.mode = OverlayCanvasView.Mode.EDIT

        touch(MotionEvent.ACTION_DOWN, 180f, 180f)
        assertThat(parent.disallowIntercept).isFalse()
    }

    @Test
    fun `pinch zooms the viewport and discards the accidental ink stroke`() {
        layoutView()
        view.mode = OverlayCanvasView.Mode.INK

        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        multiTouch(
            MotionEvent.ACTION_POINTER_DOWN,
            listOf(100f to 100f, 120f to 100f),
            actionIndex = 1,
        )
        // Span grows 20 → 40 px, so the zoom doubles.
        multiTouch(MotionEvent.ACTION_MOVE, listOf(90f to 100f, 130f to 100f))
        multiTouch(
            MotionEvent.ACTION_POINTER_UP,
            listOf(90f to 100f, 130f to 100f),
            actionIndex = 1,
        )
        touch(MotionEvent.ACTION_UP, 90f, 100f)

        assertThat(view.zoom).isWithin(1e-4f).of(2f)
        assertThat(view.commitSignature()).isNull()
    }

    @Test
    fun `ink drawn while zoomed lands at page coordinates`() {
        layoutView()
        view.mode = OverlayCanvasView.Mode.INK

        // Zoom to 2x with a pinch, then draw a 20x20 view-px stroke.
        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        multiTouch(
            MotionEvent.ACTION_POINTER_DOWN,
            listOf(100f to 100f, 120f to 100f),
            actionIndex = 1,
        )
        multiTouch(MotionEvent.ACTION_MOVE, listOf(90f to 100f, 130f to 100f))
        touch(MotionEvent.ACTION_UP, 90f, 100f)

        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_UP, 120f, 120f)

        val stroke = view.commitSignature()!!.strokes.single()
        // 20 view px at 2x zoom cover 10 page points (y grows downward on screen).
        assertThat(stroke.last().x - stroke.first().x).isWithin(1e-3f).of(10f)
        assertThat(stroke.first().y - stroke.last().y).isWithin(1e-3f).of(10f)
    }

    @Test
    fun `single-finger pan in text mode suppresses text placement`() {
        layoutView()
        view.mode = OverlayCanvasView.Mode.TEXT
        var placements = 0
        view.onTextPlacementRequested = { placements++ }

        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(MotionEvent.ACTION_UP, 150f, 150f)
        assertThat(placements).isEqualTo(0)

        touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(MotionEvent.ACTION_UP, 102f, 101f)
        assertThat(placements).isEqualTo(1)
    }

    @Test
    fun `cancel ends a text drag`() {
        val text = TextOverlay(text = "Sign here", position = PdfPoint(50f, 150f))
        view.layer = OverlayLayer(pageIndex = 0).withText(text)
        view.mode = OverlayCanvasView.Mode.EDIT

        touch(MotionEvent.ACTION_DOWN, 50f, 50f)
        touch(MotionEvent.ACTION_CANCEL, 55f, 55f)
        val positionAfterCancel = view.layer.texts.single().position

        touch(MotionEvent.ACTION_MOVE, 150f, 150f)
        assertThat(view.layer.texts.single().position).isEqualTo(positionAfterCancel)
        assertThat(parent.disallowIntercept).isFalse()
    }

    @Test
    fun `shape drag claims the touch stream and commits one shape at page coordinates`() {
        view.shapeKind = ShapeKind.RECTANGLE
        view.mode = OverlayCanvasView.Mode.SHAPE

        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        assertThat(parent.disallowIntercept).isTrue()

        touch(MotionEvent.ACTION_MOVE, 60f, 80f)
        touch(MotionEvent.ACTION_UP, 60f, 80f)
        assertThat(parent.disallowIntercept).isFalse()

        val shape = view.layer.shapes.single()
        assertThat(shape.kind).isEqualTo(ShapeKind.RECTANGLE)
        // 200pt page at 1px/pt: pixel y flips to 200 - y.
        assertThat(shape.start).isEqualTo(PdfPoint(10f, 190f))
        assertThat(shape.end).isEqualTo(PdfPoint(60f, 120f))
    }

    @Test
    fun `a shape that never leaves its start point is discarded`() {
        view.shapeKind = ShapeKind.ELLIPSE
        view.mode = OverlayCanvasView.Mode.SHAPE

        touch(MotionEvent.ACTION_DOWN, 40f, 40f)
        touch(MotionEvent.ACTION_UP, 40f, 40f)

        assertThat(view.layer.shapes).isEmpty()
    }

    @Test
    fun `undo removes the most recent shape`() {
        view.mode = OverlayCanvasView.Mode.SHAPE
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(MotionEvent.ACTION_UP, 50f, 50f)
        assertThat(view.layer.shapes).hasSize(1)

        view.undo()
        assertThat(view.layer.shapes).isEmpty()
    }
}
