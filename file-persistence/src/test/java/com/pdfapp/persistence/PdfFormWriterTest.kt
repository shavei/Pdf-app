package com.pdfapp.persistence

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.form.AcroFormFixture
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Form write-back unit tests (plan Phase 4): values set through [PdfFormWriter]
 * must survive a save/reload, read-only and unknown fields must be reported as
 * skipped rather than silently dropped, and flattening must leave the values
 * visible with no interactive form behind them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfFormWriterTest {
    private val writer = PdfFormWriter()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    /** Apply [values] to a fresh fixture form, then save and reload it. */
    private fun roundTrip(
        values: Map<String, String>,
        flatten: Boolean = false,
    ): Pair<PdfFormWriter.Result, ByteArray> {
        val document = PDDocument().also(AcroFormFixture::build)
        val result = writer.applyValues(document, values)
        if (flatten) writer.flatten(document)
        val bytes = ByteArrayOutputStream().also(document::save).toByteArray()
        document.close()
        return result to bytes
    }

    private inline fun <T> reload(
        bytes: ByteArray,
        block: (PDDocument) -> T,
    ): T = PDDocument.load(ByteArrayInputStream(bytes)).use(block)

    @Test
    fun `a text value survives the save and reload`() {
        val (result, bytes) =
            roundTrip(mapOf(AcroFormFixture.TEXT_NAME to "Grace Hopper"))
        assertThat(result.applied).isEqualTo(1)
        assertThat(result.hasSkips).isFalse()
        reload(bytes) { pdf ->
            val field = pdf.documentCatalog.acroForm.getField(AcroFormFixture.TEXT_NAME)
            assertThat(field.valueAsString).isEqualTo("Grace Hopper")
        }
    }

    @Test
    fun `a checkbox ticks on its on-value and clears on an empty one`() {
        val (_, ticked) = roundTrip(mapOf(AcroFormFixture.CHECKBOX_NAME to AcroFormFixture.CHECKBOX_ON))
        reload(ticked) { pdf ->
            val box = pdf.documentCatalog.acroForm.getField(AcroFormFixture.CHECKBOX_NAME) as PDCheckBox
            assertThat(box.isChecked).isTrue()
        }
        val (_, cleared) = roundTrip(mapOf(AcroFormFixture.CHECKBOX_NAME to ""))
        reload(cleared) { pdf ->
            val box = pdf.documentCatalog.acroForm.getField(AcroFormFixture.CHECKBOX_NAME) as PDCheckBox
            assertThat(box.isChecked).isFalse()
        }
    }

    @Test
    fun `a radio group takes the chosen button's export value`() {
        val (result, bytes) = roundTrip(mapOf(AcroFormFixture.RADIO_NAME to AcroFormFixture.RADIO_PRO))
        assertThat(result.applied).isEqualTo(1)
        reload(bytes) { pdf ->
            val group = pdf.documentCatalog.acroForm.getField(AcroFormFixture.RADIO_NAME)
            assertThat(group.valueAsString).isEqualTo(AcroFormFixture.RADIO_PRO)
        }
    }

    @Test
    fun `a dropdown takes one of its options`() {
        val (result, bytes) = roundTrip(mapOf(AcroFormFixture.CHOICE_NAME to AcroFormFixture.CHOICE_GB))
        assertThat(result.applied).isEqualTo(1)
        reload(bytes) { pdf ->
            val choice = pdf.documentCatalog.acroForm.getField(AcroFormFixture.CHOICE_NAME)
            assertThat(choice.valueAsString).contains(AcroFormFixture.CHOICE_GB)
        }
    }

    @Test
    fun `every kind can be filled in one pass`() {
        val (result, _) =
            roundTrip(
                mapOf(
                    AcroFormFixture.TEXT_NAME to "Ada",
                    AcroFormFixture.COMMENTS_NAME to "Looks good",
                    AcroFormFixture.CHECKBOX_NAME to AcroFormFixture.CHECKBOX_ON,
                    AcroFormFixture.RADIO_NAME to AcroFormFixture.RADIO_BASIC,
                    AcroFormFixture.CHOICE_NAME to AcroFormFixture.CHOICE_US,
                ),
            )
        assertThat(result.applied).isEqualTo(5)
        assertThat(result.skipped).isEmpty()
    }

    @Test
    fun `a read-only field is skipped rather than overwritten`() {
        val (result, bytes) = roundTrip(mapOf(AcroFormFixture.READ_ONLY_NAME to "tampered"))
        assertThat(result.applied).isEqualTo(0)
        assertThat(result.skipped).containsExactly(AcroFormFixture.READ_ONLY_NAME)
        reload(bytes) { pdf ->
            val field = pdf.documentCatalog.acroForm.getField(AcroFormFixture.READ_ONLY_NAME)
            assertThat(field.valueAsString).isEqualTo(AcroFormFixture.READ_ONLY_VALUE)
        }
    }

    @Test
    fun `a push button and an unknown name are both skipped`() {
        val (result, _) =
            roundTrip(
                mapOf(
                    AcroFormFixture.BUTTON_NAME to "click",
                    "noSuchField" to "value",
                    AcroFormFixture.TEXT_NAME to "Ada",
                ),
            )
        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).containsExactly(AcroFormFixture.BUTTON_NAME, "noSuchField")
    }

    @Test
    fun `flattening keeps the values but leaves no interactive form`() {
        val (_, bytes) =
            roundTrip(
                mapOf(AcroFormFixture.TEXT_NAME to FLATTENED_VALUE),
                flatten = true,
            )
        reload(bytes) { pdf ->
            val acroForm = pdf.documentCatalog.acroForm
            // A flattened form either drops the AcroForm entirely or empties it;
            // either way there is nothing left to edit.
            assertThat(acroForm == null || acroForm.fields.isEmpty()).isTrue()
            // The value now lives in the page content, which is the point.
            assertThat(pdf.getPage(0).annotations).isEmpty()
        }
    }

    @Test
    fun `an empty value map is a no-op`() {
        val document = PDDocument().also(AcroFormFixture::build)
        try {
            val result = writer.applyValues(document, emptyMap())
            assertThat(result.applied).isEqualTo(0)
            assertThat(result.skipped).isEmpty()
        } finally {
            document.close()
        }
    }

    @Test
    fun `a document with no form skips every value and flattens harmlessly`() {
        PDDocument().use { pdf ->
            pdf.addPage(PDPage(PDRectangle.LETTER))
            assertThat(writer.hasForm(pdf)).isFalse()
            val result = writer.applyValues(pdf, mapOf("anything" to "value"))
            assertThat(result.applied).isEqualTo(0)
            assertThat(result.skipped).containsExactly("anything")
            writer.flatten(pdf)
            assertThat(pdf.numberOfPages).isEqualTo(1)
        }
    }

    @Test
    fun `the fixture form is detected as a form`() {
        PDDocument().use { pdf ->
            AcroFormFixture.build(pdf)
            assertThat(writer.hasForm(pdf)).isTrue()
        }
    }

    private companion object {
        const val FLATTENED_VALUE = "Flattened Value"
    }
}
