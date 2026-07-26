package com.pdfapp.e2e

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.StrictMode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
 * Device-level test of Phase 1 "Open with" support: the manifest filter makes
 * Signet resolvable for PDF VIEW intents, and launching [MainActivity] with an
 * `ACTION_VIEW` intent loads the document straight into the editor.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithIntentTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun signet_resolves_view_intents_for_pdfs() {
        val viewPdf =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://com.example.files/doc.pdf"), "application/pdf")
        val matches = context.packageManager.queryIntentActivities(viewPdf, PackageManager.MATCH_DEFAULT_ONLY)
        assertThat(matches.map { it.activityInfo.name }).contains(MainActivity::class.java.name)
    }

    @Test
    fun view_intent_opens_the_pdf_in_the_editor() {
        val pdf = File.createTempFile("openwith", ".pdf", context.cacheDir)
        try {
            PDDocument().use { doc ->
                doc.addPage(PDPage(PDRectangle.A4))
                doc.save(pdf)
            }
            // Legacy file managers still send file:// URIs; StrictMode's VM
            // policy would abort the launch from this (targetSdk>=24) process,
            // so relax it for the test only.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(Uri.fromFile(pdf), "application/pdf")
                }

            ActivityScenario.launch<MainActivity>(intent).use {
                // The continuous reader announces each page as "Page N of M"
                // (mobile-ui-plan Phase F.2); page 1 appearing proves the
                // intent-delivered document loaded and rendered.
                composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    composeRule
                        .onAllNodesWithContentDescription(PAGE_1)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        } finally {
            pdf.delete()
        }
    }

    private companion object {
        // The reader announces a page by its position (mobile-ui-plan
        // Phase F.2). Matched exactly, so this never also picks up the
        // page chip ("Page 1 of 1, go to page"), which is a different
        // control. Instrumentation runs without a screen reader, so the
        // page text F.2 appends under TalkBack is absent here.
        val PAGE_1: String = ReaderSemantics.pageLabel(pageIndex = 0, pageCount = 1)

        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
