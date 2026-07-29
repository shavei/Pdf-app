package com.pdfapp.e2e

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdfapp.MainActivity
import com.pdfapp.ui.common.ReaderSemantics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * In-document search, end to end on a real document (plan 2.2): open, jump into
 * the middle of the file, search, and land on the hit *below* the reader rather
 * than back at the top of the document (backlog M13).
 *
 * The counter is asserted through its spoken label, which is the same string
 * TalkBack reads out, so this covers the accessibility wording too.
 */
@RunWith(AndroidJUnit4::class)
class SearchE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun search_from_the_middle_of_a_document_finds_the_next_hit_below() {
        val pdf = File.createTempFile("search", ".pdf", context.cacheDir)
        try {
            writeDocument(pdf)
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(Uri.fromFile(pdf), "application/pdf")
                }

            ActivityScenario.launch<MainActivity>(intent).use {
                awaitPage(0)

                // Read your way to page 4 — between the two hits.
                composeRule
                    .onNodeWithContentDescription(ReaderSemantics.pageChipLabel(0, PAGE_COUNT))
                    .performClick()
                // The dialog's page field is the only editable node on screen.
                composeRule.onNode(hasSetTextAction()).performTextReplacement("4")
                composeRule.onNodeWithText("Go").performClick()
                awaitPage(READ_FROM_PAGE)

                composeRule.onNodeWithContentDescription("Search in document").performClick()
                composeRule.onNode(hasSetTextAction()).performTextInput(NEEDLE)

                // The scan starts here and wraps, so it reaches page 5 first —
                // but the counter still numbers the hits the way the document
                // does, which makes it the second of two.
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription("Match 2 of 2")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                awaitPage(LATER_HIT_PAGE)

                // Stepping on wraps back to the hit above the reader — the one
                // the old page-1-first scan would have jumped to immediately.
                composeRule.onNodeWithContentDescription("Next match").performClick()
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription("Match 1 of 2")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                awaitPage(EARLIER_HIT_PAGE)
            }
        } finally {
            pdf.delete()
        }
    }

    /** Wait until the reader has [pageIndex] on screen. */
    private fun awaitPage(pageIndex: Int) {
        val label = ReaderSemantics.pageLabel(pageIndex, PAGE_COUNT)
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A document carrying the needle on exactly two pages, far apart. */
    private fun writeDocument(file: File) {
        PDDocument().use { document ->
            repeat(PAGE_COUNT) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val body = if (index == EARLIER_HIT_PAGE || index == LATER_HIT_PAGE) NEEDLE else "page $index"
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                    content.newLineAtOffset(TEXT_X, TEXT_Y)
                    content.showText(body)
                    content.endText()
                }
            }
            document.save(file)
        }
    }

    private companion object {
        const val PAGE_COUNT = 8
        const val EARLIER_HIT_PAGE = 1
        const val READ_FROM_PAGE = 3
        const val LATER_HIT_PAGE = 5
        const val NEEDLE = "kestrel"
        const val FONT_SIZE = 14f
        const val TEXT_X = 72f
        const val TEXT_Y = 700f
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
