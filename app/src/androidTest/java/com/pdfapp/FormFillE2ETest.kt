package com.pdfapp

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdfapp.core.renderer.form.AcroFormFixture
import com.pdfapp.ui.FormSemantics
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
 * UI end-to-end test of the fill-form layer (plan Phase 4): opening a real
 * AcroForm PDF surfaces the Fill form action, turning it on draws a native input
 * over every widget, and typing into one is reflected in the fill bar's progress.
 *
 * A plain PDF must not grow the action at all — which is the other half of
 * "detect the form on open".
 */
@RunWith(AndroidJUnit4::class)
class FormFillE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        // The fixture URI is a file:// URI handed to our own activity.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
    }

    private fun viewIntent(pdf: File): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

    private fun formPdf(): File =
        File.createTempFile("form-ui", ".pdf", context.cacheDir).also { file ->
            PDDocument().use { document ->
                AcroFormFixture.build(document)
                document.save(file)
            }
        }

    private fun blankPdf(): File =
        File.createTempFile("blank-ui", ".pdf", context.cacheDir).also { file ->
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle.LETTER))
                document.save(file)
            }
        }

    /** Wait until at least one node matching [description] exists. */
    private fun awaitDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            composeRule
                .onAllNodesWithContentDescription(description, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun opening_a_form_offers_fill_and_typing_updates_the_progress() {
        val pdf = formPdf()
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(pdf)).use {
                // Page 1 rendered: the reader is live.
                awaitDescription(FORM_PAGE_1)
                // Detection is a background PdfBox scan, so the action appears
                // a moment after the page does.
                awaitDescription(FILL_FORM)
                composeRule.onNodeWithContentDescription(FILL_FORM).performClick()

                // The fill bar reports how many widgets are on offer.
                awaitText(ALL_FIELDS)
                // And a native input sits over the first page's text field.
                awaitDescription(TEXT_FIELD_LABEL)
                composeRule.onNodeWithContentDescription(TEXT_FIELD_LABEL, substring = true)
                    .assertIsDisplayed()

                // Typing into it is recorded as one filled field.
                composeRule
                    .onNodeWithContentDescription(TEXT_FIELD_LABEL, substring = true)
                    .performTextReplacement(TYPED_NAME)
                awaitText(ONE_FILLED)
                composeRule.onNodeWithText("Reset").assertIsDisplayed()

                // Reset returns the bar to its untouched state.
                composeRule.onNodeWithText("Reset").performClick()
                awaitText(ALL_FIELDS)
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun closing_the_fill_bar_takes_the_inputs_off_the_page() {
        val pdf = formPdf()
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(pdf)).use {
                awaitDescription(FORM_PAGE_1)
                awaitDescription(FILL_FORM)
                composeRule.onNodeWithContentDescription(FILL_FORM).performClick()
                awaitDescription(TEXT_FIELD_LABEL)

                composeRule.onNodeWithContentDescription("Close form filling").performClick()
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(TEXT_FIELD_LABEL, substring = true)
                        .fetchSemanticsNodes()
                        .isEmpty()
                }
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun a_document_without_a_form_never_offers_to_fill_one() {
        val pdf = blankPdf()
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(pdf)).use {
                awaitDescription(BLANK_PAGE_1)
                // The reader's own chrome is up, so the scan that runs alongside
                // the (much slower) first render has long since finished — and it
                // found nothing to offer.
                awaitDescription("Edit document")
                composeRule.onNodeWithContentDescription(FILL_FORM).assertDoesNotExist()
            }
        } finally {
            pdf.delete()
        }
    }

    private companion object {
        // The fixture form is two pages; the blank comparison document is one.
        val FORM_PAGE_1: String = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = 2)
        val BLANK_PAGE_1: String = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = 1)
        const val FILL_FORM = "Fill form"
        const val TEXT_FIELD_LABEL = "${AcroFormFixture.TEXT_LABEL}, text field"
        const val TYPED_NAME = "Grace Hopper"
        val ALL_FIELDS: String =
            FormSemantics.statusText(AcroFormFixture.FILLABLE_WIDGETS, editedCount = 0)
        val ONE_FILLED: String =
            FormSemantics.statusText(AcroFormFixture.FILLABLE_WIDGETS, editedCount = 1)
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
