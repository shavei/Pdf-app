package com.pdfapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.form.AcroFormFixture
import com.pdfapp.core.renderer.form.FormFieldKind
import com.pdfapp.core.renderer.text.PdfTextDocument
import com.pdfapp.persistence.PdfFormWriter
import com.pdfapp.persistence.PdfSaver
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-level end-to-end test of the forms pipeline (plan Phase 4) with real
 * on-device PdfBox: read an AcroForm's widgets through `:core-renderer`, write
 * values back and flatten through `:file-persistence`, save, reload, and verify.
 *
 * The JVM harness covers the same ground under Robolectric; this proves it also
 * holds on a device, where PdfBox's font and appearance handling is the real
 * Android implementation rather than a shadow.
 */
@RunWith(AndroidJUnit4::class)
class FormPipelineE2ETest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    private fun writeFixture(name: String): File =
        File.createTempFile(name, ".pdf", context.cacheDir).also { file ->
            PDDocument().use { document ->
                AcroFormFixture.build(document)
                document.save(file)
            }
        }

    @Test
    fun form_widgets_are_read_then_filled_flattened_and_saved() {
        val source = writeFixture("form-src")
        val output = File.createTempFile("form-out", ".pdf", context.cacheDir)
        try {
            // 1. Read the form through the production reader.
            val fields =
                source.inputStream().use { input ->
                    PdfTextDocument.load(input).use { it.formFields() }
                }
            assertThat(fields).hasSize(AcroFormFixture.FILLABLE_WIDGETS)
            assertThat(fields.map { it.kind }.toSet())
                .containsExactly(
                    FormFieldKind.TEXT,
                    FormFieldKind.CHECKBOX,
                    FormFieldKind.RADIO,
                    FormFieldKind.CHOICE,
                )
            // Every widget resolved to a real page and a drawable rectangle.
            assertThat(fields.all { it.pageIndex in 0..1 }).isTrue()
            assertThat(fields.all { it.box.width > 0f && it.box.height > 0f }).isTrue()

            // 2. Fill, flatten and save through the production writer.
            val values =
                mapOf(
                    AcroFormFixture.TEXT_NAME to FILLED_NAME,
                    AcroFormFixture.CHECKBOX_NAME to AcroFormFixture.CHECKBOX_ON,
                    AcroFormFixture.RADIO_NAME to AcroFormFixture.RADIO_BASIC,
                    AcroFormFixture.CHOICE_NAME to AcroFormFixture.CHOICE_US,
                )
            source.inputStream().use { input ->
                PDDocument.load(input).use { pdf ->
                    val writer = PdfFormWriter()
                    assertThat(writer.hasForm(pdf)).isTrue()
                    val result = writer.applyValues(pdf, values)
                    assertThat(result.applied).isEqualTo(values.size)
                    assertThat(result.skipped).isEmpty()
                    writer.flatten(pdf)
                    PdfSaver().writeTo(pdf, output.outputStream())
                }
            }

            // 3. Reload: the value is page content and nothing is left to edit.
            PDDocument.load(output).use { reloaded ->
                assertThat(PDFTextStripper().getText(reloaded)).contains(FILLED_NAME)
                val acroForm = reloaded.documentCatalog.acroForm
                assertThat(acroForm == null || acroForm.fields.isEmpty()).isTrue()
                assertThat(reloaded.getPage(0).annotations).isEmpty()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun saving_without_flattening_keeps_the_form_fillable_on_device() {
        val source = writeFixture("form-editable-src")
        val output = File.createTempFile("form-editable-out", ".pdf", context.cacheDir)
        try {
            source.inputStream().use { input ->
                PDDocument.load(input).use { pdf ->
                    PdfFormWriter().applyValues(pdf, mapOf(AcroFormFixture.TEXT_NAME to FILLED_NAME))
                    PdfSaver().writeTo(pdf, output.outputStream())
                }
            }
            // Read it back through the reader the UI uses: the value shows up as
            // the field's current value, ready to be edited again.
            val fields =
                output.inputStream().use { input ->
                    PdfTextDocument.load(input).use { it.formFields() }
                }
            val text = fields.first { it.name == AcroFormFixture.TEXT_NAME }
            assertThat(text.value).isEqualTo(FILLED_NAME)
            assertThat(text.readOnly).isFalse()
        } finally {
            source.delete()
            output.delete()
        }
    }

    private companion object {
        const val FILLED_NAME = "Grace Hopper"
    }
}
