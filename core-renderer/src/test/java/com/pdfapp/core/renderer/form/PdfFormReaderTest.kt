package com.pdfapp.core.renderer.form

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.text.PdfTextDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Reads the hand-built [AcroFormFixture] through the production API (plan Phase
 * 4) and asserts every property the fill UI depends on: kind, page, rectangle,
 * value, flags, options and per-widget "on" states. Runs headless on the JVM via
 * Robolectric (PdfBox needs a Context for its resource loader).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfFormReaderTest {
    private var document: PdfTextDocument? = null

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        document?.close()
    }

    private fun load(bytes: ByteArray): List<PdfFormField> =
        PdfTextDocument.load(ByteArrayInputStream(bytes)).let {
            document = it
            it.formFields()
        }

    private fun fixtureFields(): List<PdfFormField> = load(AcroFormFixture.bytes())

    private fun field(name: String): PdfFormField = fixtureFields().first { it.name == name }

    @Test
    fun `every fillable widget is found and push buttons are not`() {
        val fields = fixtureFields()
        assertThat(fields).hasSize(AcroFormFixture.FILLABLE_WIDGETS)
        assertThat(fields.map { it.name }).doesNotContain(AcroFormFixture.BUTTON_NAME)
    }

    @Test
    fun `a text field reports its kind, value, label and page`() {
        val text = field(AcroFormFixture.TEXT_NAME)
        assertThat(text.kind).isEqualTo(FormFieldKind.TEXT)
        assertThat(text.value).isEqualTo(AcroFormFixture.TEXT_VALUE)
        assertThat(text.label).isEqualTo(AcroFormFixture.TEXT_LABEL)
        assertThat(text.displayName).isEqualTo(AcroFormFixture.TEXT_LABEL)
        assertThat(text.pageIndex).isEqualTo(0)
        assertThat(text.readOnly).isFalse()
        assertThat(text.multiline).isFalse()
        assertThat(text.maxLength).isNull()
        assertThat(text.fontSizePt).isEqualTo(AcroFormFixture.TEXT_FONT_SIZE_PT)
    }

    @Test
    fun `the widget rectangle survives as pdf user space points`() {
        val box = field(AcroFormFixture.TEXT_NAME).box
        assertThat(box.left).isWithin(TOLERANCE).of(72f)
        assertThat(box.bottom).isWithin(TOLERANCE).of(700f)
        assertThat(box.width).isWithin(TOLERANCE).of(200f)
        assertThat(box.height).isWithin(TOLERANCE).of(20f)
    }

    @Test
    fun `a multiline text field reports its flag and character limit`() {
        val comments = field(AcroFormFixture.COMMENTS_NAME)
        assertThat(comments.multiline).isTrue()
        assertThat(comments.maxLength).isEqualTo(AcroFormFixture.COMMENTS_MAX_LEN)
        assertThat(comments.value).isEmpty()
    }

    @Test
    fun `a checkbox reports its on value and starts unticked`() {
        val agree = field(AcroFormFixture.CHECKBOX_NAME)
        assertThat(agree.kind).isEqualTo(FormFieldKind.CHECKBOX)
        assertThat(agree.onValue).isEqualTo(AcroFormFixture.CHECKBOX_ON)
        assertThat(agree.isOn).isFalse()
    }

    @Test
    fun `a radio group yields one widget per button, each with its own export value`() {
        val buttons = fixtureFields().filter { it.name == AcroFormFixture.RADIO_NAME }
        assertThat(buttons).hasSize(2)
        assertThat(buttons.map { it.kind }.distinct()).containsExactly(FormFieldKind.RADIO)
        assertThat(buttons.map { it.onValue })
            .containsExactly(AcroFormFixture.RADIO_BASIC, AcroFormFixture.RADIO_PRO)
        // Same field, so the same write-back key — told apart by widget index.
        assertThat(buttons.map { it.widgetId }).containsNoDuplicates()
        assertThat(buttons.map { it.pageIndex }.distinct()).containsExactly(0)
    }

    @Test
    fun `a dropdown reports its options with display labels`() {
        val country = field(AcroFormFixture.CHOICE_NAME)
        assertThat(country.kind).isEqualTo(FormFieldKind.CHOICE)
        assertThat(country.pageIndex).isEqualTo(1)
        assertThat(country.options.map { it.value })
            .containsExactly(AcroFormFixture.CHOICE_US, AcroFormFixture.CHOICE_GB)
        assertThat(country.options.map { it.label })
            .containsExactly(AcroFormFixture.CHOICE_US_LABEL, AcroFormFixture.CHOICE_GB_LABEL)
    }

    @Test
    fun `a read-only field is reported so the UI can show it without a caret`() {
        val reference = field(AcroFormFixture.READ_ONLY_NAME)
        assertThat(reference.readOnly).isTrue()
        assertThat(reference.value).isEqualTo(AcroFormFixture.READ_ONLY_VALUE)
    }

    @Test
    fun `a field without a tooltip falls back to the last segment of its name`() {
        val comments = field(AcroFormFixture.COMMENTS_NAME)
        assertThat(comments.label).isEmpty()
        assertThat(comments.displayName).isEqualTo(AcroFormFixture.COMMENTS_NAME)
    }

    @Test
    fun `a document without a form yields no fields and is not reported as XFA`() {
        val bytes =
            PDDocument().use { pdf ->
                pdf.addPage(PDPage(PDRectangle.LETTER))
                ByteArrayOutputStream().also(pdf::save).toByteArray()
            }
        assertThat(load(bytes)).isEmpty()
        assertThat(document?.isXfaOnlyForm()).isFalse()
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
