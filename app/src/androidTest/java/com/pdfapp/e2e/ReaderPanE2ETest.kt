package com.pdfapp.e2e

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.pdfapp.MainActivity
import com.pdfapp.ui.common.ReaderSemantics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reader's viewport gestures, on a device.
 *
 * Both cases guard behaviour the old two-scroll-container layout could not
 * produce: a zoom that stays anchored on the point you tapped (the horizontal
 * anchor used to be clamped away to the left edge), and a single drag that pans
 * horizontally *and* scrolls vertically (nested scroll containers lock a drag
 * to one axis). Positions come from the page node's unclipped position in the
 * root, which moves with the pan offset.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPanE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    /** The page-1 node; its content description carries the document's page count. */
    private var pageLabel: String = ""

    private fun pageNode() = composeRule.onNodeWithContentDescription(pageLabel).fetchSemanticsNode()

    // `positionInRoot`, not `boundsInRoot`: bounds are clipped to the viewport,
    // so a page panned off the left edge reports 0 instead of how far it moved —
    // which is exactly the quantity under test.
    private fun pageLeft(): Float = pageNode().positionInRoot.x

    private fun pageTop(): Float = pageNode().positionInRoot.y

    private fun pageWidth(): Int = pageNode().size.width

    private fun viewportSize(): Pair<Float, Float> =
        composeRule.onRoot().fetchSemanticsNode().size.let { it.width.toFloat() to it.height.toFloat() }

    /** Open [pageCount] blank A4 pages in the reader and wait for page 1. */
    private fun withReader(
        pageCount: Int,
        body: () -> Unit,
    ) {
        pageLabel = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = pageCount)
        val pdf = File.createTempFile("readerpan", ".pdf", context.cacheDir)
        try {
            PDDocument().use { doc ->
                repeat(pageCount) { doc.addPage(PDPage(PDRectangle.A4)) }
                doc.save(pdf)
            }
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(Uri.fromFile(pdf), "application/pdf")
                }
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(pageLabel)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                body()
            }
        } finally {
            pdf.delete()
        }
    }

    /** Double-tap at [x] of the viewport width, vertically centred, and let it settle. */
    private fun doubleTapAt(x: Float) {
        val (width, height) = viewportSize()
        val fitWidth = pageWidth()
        composeRule.onRoot().performTouchInput {
            doubleClick(Offset(width * x, height / 2f))
        }
        composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS) {
            pageWidth() > fitWidth * ZOOM_IN_THRESHOLD
        }
        composeRule.waitForIdle()
    }

    @Test
    fun doubleTap_keepsTheTappedPointInPlace() {
        withReader(pageCount = 1) {
            val (width, _) = viewportSize()
            assertThat(pageLeft()).isWithin(TOLERANCE_PX).of(0f)

            // Double-tap 80% across the page: at 2.5x, holding that point still
            // needs the document panned 1.2 viewports to the right, so the
            // page's left edge leaves the screen. Clamped to the pre-zoom width
            // (the old behaviour) it could not move at all.
            doubleTapAt(x = 0.8f)

            assertThat(pageLeft()).isLessThan(-width)
        }
    }

    @Test
    fun oneFingerDrag_pansAndScrollsInTheSameGesture() {
        withReader(pageCount = 3) {
            val (width, height) = viewportSize()
            doubleTapAt(x = 0.5f)

            val leftBefore = pageLeft()
            val topBefore = pageTop()

            // One diagonal drag up and to the left: the document should follow
            // in both axes at once.
            composeRule.onRoot().performTouchInput {
                swipe(
                    start = Offset(width * 0.7f, height * 0.7f),
                    end = Offset(width * 0.4f, height * 0.4f),
                    durationMillis = SWIPE_MS,
                )
            }
            composeRule.waitForIdle()

            assertThat(pageLeft()).isLessThan(leftBefore - TOLERANCE_PX)
            assertThat(pageTop()).isLessThan(topBefore - TOLERANCE_PX)
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
        const val ZOOM_TIMEOUT_MS = 5_000L
        const val SWIPE_MS = 300L

        // The reading zoom is 2.5x; require clearly-more-than-fit so a stray
        // single tap (no zoom) can never satisfy the wait.
        const val ZOOM_IN_THRESHOLD = 1.5f

        // Sub-pixel rounding drift in the committed offsets.
        const val TOLERANCE_PX = 2f
    }
}
