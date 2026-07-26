package com.pdfapp.persistence

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.form.AcroFormFixture
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * PDF-Test-Harness, forms half (plan Phase 4). Where
 * [PdfTestHarnessTest] smoke-tests the overlay write-back pipeline, this covers
 * the AcroForm one — and crucially the *combination*, which is what "fill & sign"
 * means: values written back with [PdfFormWriter], the form flattened, and the
 * ink signature burned in on top, all in the one save the ViewModel performs.
 *
 * Runs headless on the JVM via Robolectric (PdfBox needs a Context for its
 * resource loader).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTestHarnessFormsTest {
    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    /**
     * The production save order from `PdfEditorViewModel.save`: apply values,
     * optionally flatten the form, then flatten the overlays on top.
     */
    private fun save(
        file: File,
        values: Map<String, String>,
        flattenForm: Boolean,
        layer: OverlayLayer? = null,
    ): PdfFormWriter.Result {
        val document = PDDocument().also(AcroFormFixture::build)
        return try {
            val writer = PdfFormWriter()
            val result = writer.applyValues(document, values)
            if (flattenForm) writer.flatten(document)
            layer?.let { PdfFlattener(RuntimeEnvironment.getApplication()).flattenInto(document, it) }
            PdfSaver().writeTo(document, file.outputStream())
            result
        } finally {
            document.close()
        }
    }

    @Test
    fun `filled values and an ink signature survive one save together`() {
        val file = File.createTempFile("harness-form-signed", ".pdf")
        try {
            val layer =
                OverlayLayer(pageIndex = 0)
                    .withText(TextOverlay(SIGNED_BY, PdfPoint(x = 72f, y = 400f)))
                    .withSignature(
                        InkSignature(
                            strokes = listOf(listOf(PdfPoint(72f, 300f), PdfPoint(180f, 340f))),
                        ),
                    )
            val result =
                save(
                    file,
                    values =
                        mapOf(
                            AcroFormFixture.TEXT_NAME to FILLED_NAME,
                            AcroFormFixture.CHECKBOX_NAME to AcroFormFixture.CHECKBOX_ON,
                            AcroFormFixture.RADIO_NAME to AcroFormFixture.RADIO_PRO,
                            AcroFormFixture.CHOICE_NAME to AcroFormFixture.CHOICE_GB,
                        ),
                    flattenForm = true,
                    layer = layer,
                )
            assertThat(result.applied).isEqualTo(4)
            assertThat(result.skipped).isEmpty()

            PDDocument.load(file).use { reloaded ->
                assertThat(reloaded.numberOfPages).isEqualTo(2)
                val text = PDFTextStripper().getText(reloaded)
                // The flattened field value and the overlay text are both now
                // page content, so one extraction proves both landed.
                assertThat(text).contains(FILLED_NAME)
                assertThat(text).contains(SIGNED_BY)
                // Nothing interactive is left to edit after flattening.
                val acroForm = reloaded.documentCatalog.acroForm
                assertThat(acroForm == null || acroForm.fields.isEmpty()).isTrue()
                // And the signature is still a vector path on page 1.
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
    fun `saving without flattening leaves the form editable with its new values`() {
        val file = File.createTempFile("harness-form-editable", ".pdf")
        try {
            save(
                file,
                values = mapOf(AcroFormFixture.TEXT_NAME to FILLED_NAME),
                flattenForm = false,
            )
            PDDocument.load(file).use { reloaded ->
                val acroForm = reloaded.documentCatalog.acroForm
                assertThat(acroForm).isNotNull()
                val field = acroForm.getField(AcroFormFixture.TEXT_NAME)
                assertThat(field.valueAsString).isEqualTo(FILLED_NAME)
                assertThat(field.isReadOnly).isFalse()
                // Widgets still there: the recipient can change what was typed.
                assertThat(reloaded.getPage(0).annotations).isNotEmpty()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an unfilled form saves unchanged`() {
        val file = File.createTempFile("harness-form-untouched", ".pdf")
        try {
            val result = save(file, values = emptyMap(), flattenForm = false)
            assertThat(result.applied).isEqualTo(0)
            PDDocument.load(file).use { reloaded ->
                val field = reloaded.documentCatalog.acroForm.getField(AcroFormFixture.TEXT_NAME)
                assertThat(field.valueAsString).isEqualTo(AcroFormFixture.TEXT_VALUE)
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val FILLED_NAME = "Grace Hopper"
        const val SIGNED_BY = "SIGNED-BY-HARNESS"

        // A standalone PDF stroke operator, as in the overlay harness.
        val STROKE_OPERATOR = Regex("""(^|\s)S(\s|$)""")
    }
}
