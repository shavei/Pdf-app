package com.pdfapp

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdfapp.ui.ReaderSemantics
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
 * Phase D.1 (mobile-ui-plan): double-tapping the page actually zooms the
 * document. Proves the gesture is wired end-to-end — not just that the toggle
 * math is right (that's `ReaderZoomTest`) — by measuring the rendered page's
 * layout width before and after a double-tap. A first double-tap from fit-width
 * grows it; a second toggles it back.
 */
@RunWith(AndroidJUnit4::class)
class DoubleTapZoomE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    private fun pageWidthPx(): Int =
        composeRule
            .onNodeWithContentDescription(PAGE_1, substring = true)
            .fetchSemanticsNode()
            .size
            .width

    @Test
    fun doubleTap_zooms_the_page_in_then_back_out() {
        val pdf = File.createTempFile("doubletap", ".pdf", context.cacheDir)
        try {
            PDDocument().use { doc ->
                doc.addPage(PDPage(PDRectangle.A4))
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
                        .onAllNodesWithContentDescription(PAGE_1, substring = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }

                val fitWidth = pageWidthPx()

                // Double-tap the page: fit-width → the comfortable reading zoom.
                composeRule.onNodeWithContentDescription(PAGE_1, substring = true).performTouchInput { doubleClick() }
                composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS) {
                    pageWidthPx() > fitWidth * ZOOM_IN_THRESHOLD
                }

                // Double-tap again: zoomed-in → back to fit-width.
                composeRule.onNodeWithContentDescription(PAGE_1, substring = true).performTouchInput { doubleClick() }
                composeRule.waitUntil(timeoutMillis = ZOOM_TIMEOUT_MS) {
                    pageWidthPx() <= fitWidth + ZOOM_OUT_TOLERANCE_PX
                }
            }
        } finally {
            pdf.delete()
        }
    }

    private companion object {
        // The reader announces a page by its position (mobile-ui-plan
        // Phase F.2), and appends the page text when a screen reader is
        // running — so match on the position prefix.
        val PAGE_1: String = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = 1)

        const val LOAD_TIMEOUT_MS = 10_000L
        const val ZOOM_TIMEOUT_MS = 5_000L

        // The reading zoom is 2.5×; require clearly-more-than-fit so a stray
        // single tap (no zoom) can never satisfy the wait.
        const val ZOOM_IN_THRESHOLD = 1.5f

        // Baking the zoom back to fit can leave sub-pixel rounding drift.
        const val ZOOM_OUT_TOLERANCE_PX = 2
    }
}
