package com.pdfapp

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.PageRenderer
import com.pdfapp.core.renderer.PdfDocumentSource
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.pdfapp.persistence.PdfFlattener
import com.pdfapp.persistence.PdfSaver
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-level end-to-end test of the full pipeline using the *real* Android
 * `PdfRenderer` (which Robolectric cannot exercise) plus on-device PdfBox
 * flatten/save: render a page, burn in a text overlay and a signature, save,
 * reload, and verify both persisted.
 */
@RunWith(AndroidJUnit4::class)
class PipelineEndToEndTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val overlayText = "E2E-OVERLAY"

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun render_then_flatten_then_save_round_trips() {
        val srcFile = File.createTempFile("src", ".pdf", context.cacheDir)
        val outFile = File.createTempFile("out", ".pdf", context.cacheDir)
        try {
            // Create a blank A4 PDF on device storage.
            PDDocument().use { doc ->
                doc.addPage(PDPage(PDRectangle.A4))
                doc.save(srcFile)
            }

            // Render page 0 with the real Android PdfRenderer.
            val uri = Uri.fromFile(srcFile)
            PdfDocumentSource.fromUri(context.contentResolver, uri).use { source ->
                assertThat(source.pageCount).isEqualTo(1)
                val rendered =
                    runBlocking {
                        PageRenderer(source).renderPage(index = 0, pixelsPerPoint = 2f)
                    }
                assertThat(rendered.bitmap.width).isGreaterThan(0)
                assertThat(rendered.pageSize.widthPt).isWithin(1f).of(PDRectangle.A4.width)
            }

            // Flatten a text overlay + signature into a fresh copy and save.
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(overlayText, PdfPoint(72f, 700f)))
                    .withSignature(
                        InkSignature(strokes = listOf(listOf(PdfPoint(72f, 120f), PdfPoint(200f, 160f)))),
                    )
            context.contentResolver.openInputStream(uri)!!.use { input ->
                PDDocument.load(input).use { pdf ->
                    PdfFlattener().flattenInto(pdf, layer)
                    PdfSaver().writeTo(pdf, outFile.outputStream())
                }
            }

            // Reload and verify both overlays persisted.
            PDDocument.load(outFile).use { reloaded ->
                assertThat(reloaded.numberOfPages).isEqualTo(1)
                assertThat(PDFTextStripper().getText(reloaded)).contains(overlayText)
                val content =
                    reloaded
                        .getPage(0)
                        .contents
                        .bufferedReader()
                        .use { it.readText() }
                assertThat(STROKE_OPERATOR.containsMatchIn(content)).isTrue()
            }
        } finally {
            srcFile.delete()
            outFile.delete()
        }
    }

    private companion object {
        val STROKE_OPERATOR = Regex("""(^|\s)S(\s|$)""")
    }
}
