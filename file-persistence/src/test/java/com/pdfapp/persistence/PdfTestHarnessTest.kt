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

    private fun flattener() = PdfFlattener(RuntimeEnvironment.getApplication())

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
            flattener().flattenInto(document, layer)
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
    fun `hebrew text overlay survives the flatten and save round trip`() {
        // Regression test for the Save crash "U+05D9 ('afii57673') is not
        // available in the font Helvetica": non-WinAnsi text must fall back to
        // the bundled Unicode font instead of throwing.
        val hebrew = "שלום עולם"
        val file = File.createTempFile("harness-hebrew", ".pdf")
        try {
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(hebrew, PdfPoint(x = 72f, y = 720f)))

            flattener().flattenInto(document, layer)
            PdfSaver().writeTo(document, file.outputStream())
            document.close()

            PDDocument.load(file).use { reloaded ->
                val extracted = PDFTextStripper().getText(reloaded)
                // Glyphs are placed in visual order, so depending on the
                // stripper's own bidi handling the text comes back in logical
                // or visual (reversed) order — either proves the round trip.
                assertThat(
                    extracted.contains(hebrew) || extracted.contains(hebrew.reversed()),
                ).isTrue()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `mixed latin and hebrew text overlay flattens without error`() {
        val mixed = "Signed by יעל on 2026-07-13"
        val file = File.createTempFile("harness-mixed", ".pdf")
        try {
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(mixed, PdfPoint(x = 72f, y = 680f)))

            flattener().flattenInto(document, layer)
            PdfSaver().writeTo(document, file.outputStream())
            document.close()

            PDDocument.load(file).use { reloaded ->
                val extracted = PDFTextStripper().getText(reloaded)
                assertThat(extracted).contains("Signed by")
                assertThat(extracted).contains("יעל")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `latin-only text still uses built-in helvetica without embedding a font file`() {
        val file = File.createTempFile("harness-latin", ".pdf")
        try {
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(overlayText, PdfPoint(x = 72f, y = 720f)))
            flattener().flattenInto(document, layer)
            PdfSaver().writeTo(document, file.outputStream())
            document.close()

            // Pure-Latin output must stay small: no embedded font program.
            assertThat(file.length()).isLessThan(EMBEDDED_FONT_THRESHOLD_BYTES)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `flattening an empty layer leaves the page text unchanged`() {
        val file = File.createTempFile("harness-empty", ".pdf")
        try {
            val document = PDDocument().apply { addPage(PDPage(PDRectangle.A4)) }
            flattener().flattenInto(document, OverlayLayer(pageIndex = 0))
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

        // A blank page + Helvetica text is ~1 KB; even a subset font program
        // adds tens of KB. Anything under this cannot contain an embedded font.
        const val EMBEDDED_FONT_THRESHOLD_BYTES = 10_000L
    }
}
