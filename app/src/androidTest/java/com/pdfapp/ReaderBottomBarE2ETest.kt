package com.pdfapp

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * Phase B.2 (mobile-ui-plan): with a document open in READ mode, the reader
 * bottom bar hosts the primary actions, and tapping its Edit action lands in
 * EDIT mode.
 */
@RunWith(AndroidJUnit4::class)
class ReaderBottomBarE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun bottomBar_edit_action_enters_edit_mode() {
        val pdf = File.createTempFile("bottombar", ".pdf", context.cacheDir)
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
                // Wait for the document to render (proves READ mode is live).
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription("Page 1")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                // The bottom bar hosts Edit; tapping it enters EDIT mode, whose
                // top bar exposes the "Back to reading" navigation icon.
                composeRule.onNodeWithContentDescription("Edit document").assertIsDisplayed()
                composeRule.onNodeWithContentDescription("Edit document").performClick()
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription("Back to reading")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        } finally {
            pdf.delete()
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
