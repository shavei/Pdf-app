package com.pdfapp.persistence

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * PDF-Test-Harness — the project's end-to-end smoke test for the write-back
 * pipeline. It generates a blank PDF, programmatically adds a mock text overlay
 * and signature through the real [PdfFlattener] / [PdfSaver] APIs, saves to disk,
 * re-opens, and asserts the overlays persisted. Runs headless on the JVM via
 * Robolectric (PdfBox needs a Context for its resource loader).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTestHarnessTest {
    private val overlayText = "PDF-TEST-OVERLAY"

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `mock text overlay and signature are added and saved into a blank pdf`() {
        val file = File.createTempFile("harness", ".pdf")
        try {
            // 1. Generate a blank A4 PDF in memory.
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }

            // 2. Build a mock overlay layer with text + a 2-stroke signature.
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(overlayText, PdfPoint(x = 72f, y = 720f)))
                    .withSignature(
                        InkSignature(
                            strokes =
                                listOf(
                                    listOf(PdfPoint(72f, 120f), PdfPoint(140f, 160f), PdfPoint(210f, 120f)),
                                    listOf(PdfPoint(140f, 100f), PdfPoint(140f, 180f)),
                                ),
                        ),
                    )

            // 3. Flatten and 4. save through the production code paths.
            PdfFlattener().flattenInto(document, layer)
            PdfSaver().writeTo(document, file.outputStream())
            document.close()

            // 5. Re-open and assert the overlays persisted.
            PDDocument.load(file).use { reloaded ->
                assertThat(reloaded.numberOfPages).isEqualTo(1)

                val extracted = PDFTextStripper().getText(reloaded)
                assertThat(extracted).contains(overlayText)

                // The signature is a vector path: assert a stroke operator was
                // emitted into the (decoded) page content stream.
                val content =
                    reloaded
                        .getPage(0)
                        .contents
                        .bufferedReader()
                        .use { it.readText() }
                assertThat(STROKE_OPERATOR.containsMatchIn(content)).isTrue()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `flattening an empty layer leaves the page text unchanged`() {
        val file = File.createTempFile("harness-empty", ".pdf")
        try {
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }
            PdfFlattener().flattenInto(document, OverlayLayer(pageIndex = 0))
            PdfSaver().writeTo(document, file.outputStream())
            document.close()

            PDDocument.load(file).use { reloaded ->
                assertThat(PDFTextStripper().getText(reloaded).trim()).isEmpty()
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        // A standalone PDF stroke operator "S" surrounded by whitespace — avoids
        // matching the "S" inside the overlay text string "PDF-TEST-OVERLAY".
        val STROKE_OPERATOR = Regex("""(^|\s)S(\s|$)""")
    }
}
