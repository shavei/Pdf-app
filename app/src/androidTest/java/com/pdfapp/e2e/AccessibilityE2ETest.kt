package com.pdfapp.e2e

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdfapp.MainActivity
import com.pdfapp.ui.common.ReaderSemantics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase F.2 (mobile-ui-plan): what a screen reader actually gets from the
 * reader, end-to-end on a device.
 *
 * `ReaderSemanticsTest` pins the wording on the JVM; this proves the wording is
 * wired to the real nodes — a rendered page is one node that announces its
 * position, its click action is the immersive chrome toggle (the one reader
 * gesture a screen reader cannot otherwise perform), and the thumbnail grid
 * marks the current page.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    private fun page1() = composeRule.onNodeWithContentDescription(PAGE_1)

    /** The label a screen reader offers for the page's click action. */
    private fun chromeActionLabel(): String? =
        page1().fetchSemanticsNode().config
            .getOrNull(SemanticsActions.OnClick)
            ?.label

    @Test
    fun a_rendered_page_announces_its_position_and_toggles_the_chrome() {
        val pdf = twoPageDocument()
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(pdf)).use {
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(PAGE_1)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }

                // Chrome starts visible, so the offered action is to hide it.
                assertEquals(ReaderSemantics.chromeToggleLabel(true), chromeActionLabel())

                page1().performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil(timeoutMillis = TOGGLE_TIMEOUT_MS) {
                    chromeActionLabel() == ReaderSemantics.chromeToggleLabel(false)
                }

                // ...and back, so the action is never a one-way trip.
                page1().performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil(timeoutMillis = TOGGLE_TIMEOUT_MS) {
                    chromeActionLabel() == ReaderSemantics.chromeToggleLabel(true)
                }
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun the_thumbnail_grid_announces_each_page_and_marks_the_current_one() {
        val pdf = twoPageDocument()
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(pdf)).use {
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(PAGE_1)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                composeRule.onNodeWithContentDescription("Page thumbnails").performClick()

                // One node per cell — not an image plus a loose page number.
                val current = ReaderSemantics.thumbnailLabel(0, PAGE_COUNT, isCurrent = true)
                val other = ReaderSemantics.thumbnailLabel(1, PAGE_COUNT, isCurrent = false)
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule.onAllNodesWithContentDescription(current)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                composeRule.onNodeWithContentDescription(other).assertExists()
            }
        } finally {
            pdf.delete()
        }
    }

    private fun twoPageDocument(): File {
        val pdf = File.createTempFile("a11y", ".pdf", context.cacheDir)
        PDDocument().use { doc ->
            repeat(PAGE_COUNT) { doc.addPage(PDPage(PDRectangle.A4)) }
            doc.save(pdf)
        }
        // Legacy file managers still send file:// URIs; StrictMode's VM policy
        // would abort the launch from this (targetSdk>=24) process.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        return pdf
    }

    private fun viewIntent(pdf: File) =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

    private companion object {
        const val PAGE_COUNT = 2
        const val LOAD_TIMEOUT_MS = 10_000L
        const val TOGGLE_TIMEOUT_MS = 5_000L
        val PAGE_1: String = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = PAGE_COUNT)
    }
}
