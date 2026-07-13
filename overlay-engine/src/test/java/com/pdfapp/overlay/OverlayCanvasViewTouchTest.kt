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
}
