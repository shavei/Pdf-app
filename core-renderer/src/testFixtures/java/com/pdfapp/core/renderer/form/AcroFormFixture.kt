package com.pdfapp.core.renderer.form

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.ByteArrayOutputStream

/**
 * Builds a real, two-page AcroForm PDF in memory for the form tests: one field of
 * every kind the app fills, plus a push button and a read-only field, which it
 * must not offer to fill.
 *
 * Hand-built rather than checked in as a binary so every rectangle, export value
 * and flag the reader is asserted against is visible right here — and so the
 * fixture stays greppable when a PdfBox upgrade changes a default.
 */
object AcroFormFixture {
    const val TEXT_NAME = "fullName"
    const val TEXT_LABEL = "Full name"
    const val TEXT_VALUE = "Ada Lovelace"
    const val COMMENTS_NAME = "comments"
    const val COMMENTS_MAX_LEN = 40
    const val CHECKBOX_NAME = "agree"
    const val CHECKBOX_ON = "Yes"
    const val RADIO_NAME = "plan"
    const val RADIO_BASIC = "basic"
    const val RADIO_PRO = "pro"
    const val CHOICE_NAME = "country"
    const val CHOICE_US = "US"
    const val CHOICE_GB = "GB"
    const val CHOICE_US_LABEL = "United States"
    const val CHOICE_GB_LABEL = "United Kingdom"
    const val BUTTON_NAME = "submit"
    const val READ_ONLY_NAME = "reference"
    const val READ_ONLY_VALUE = "REF-1234"

    /** How many widgets the reader should find: 4 on page 1 (two radios), 2 on page 2. */
    const val FILLABLE_WIDGETS = 7

    const val TEXT_FONT_SIZE_PT = 10f

    /** PDF bytes of the fixture form. */
    fun bytes(): ByteArray =
        PDDocument().use { document ->
            build(document)
            ByteArrayOutputStream().also(document::save).toByteArray()
        }

    /** Populate [document] with the fixture's two pages and its form. */
    fun build(document: PDDocument) {
        val first = PDPage(PDRectangle.LETTER)
        val second = PDPage(PDRectangle.LETTER)
        document.addPage(first)
        document.addPage(second)

        val acroForm = PDAcroForm(document)
        acroForm.defaultResources =
            PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
        acroForm.defaultAppearance = DEFAULT_APPEARANCE
        document.documentCatalog.acroForm = acroForm

        addTextField(acroForm, first)
        addCommentsField(acroForm, first)
        addCheckBox(document, acroForm, first)
        addRadioGroup(document, acroForm, first)
        addComboBox(acroForm, second)
        addPushButton(acroForm, second)
        addReadOnlyField(acroForm, second)
    }

    private fun addTextField(
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDTextField(acroForm)
        field.partialName = TEXT_NAME
        field.alternateFieldName = TEXT_LABEL
        field.defaultAppearance = DEFAULT_APPEARANCE
        // Set /V straight into the COS dictionary: PDField.setValue would also
        // run the appearance generator, which is the writer's behaviour to
        // exercise, not the fixture's.
        field.cosObject.setString(COSName.V, TEXT_VALUE)
        attach(acroForm, field, page, PDRectangle(72f, 700f, 200f, 20f))
    }

    private fun addCommentsField(
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDTextField(acroForm)
        field.partialName = COMMENTS_NAME
        field.defaultAppearance = DEFAULT_APPEARANCE
        field.isMultiline = true
        field.maxLen = COMMENTS_MAX_LEN
        attach(acroForm, field, page, PDRectangle(72f, 600f, 200f, 60f))
    }

    private fun addCheckBox(
        document: PDDocument,
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDCheckBox(acroForm)
        field.partialName = CHECKBOX_NAME
        val widget = attach(acroForm, field, page, PDRectangle(72f, 560f, 12f, 12f))
        widget.appearance = toggleAppearance(document, CHECKBOX_ON)
    }

    private fun addRadioGroup(
        document: PDDocument,
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDRadioButton(acroForm)
        field.partialName = RADIO_NAME
        field.exportValues = listOf(RADIO_BASIC, RADIO_PRO)
        acroForm.fields.add(field)
        val widgets =
            listOf(RADIO_BASIC to 500f, RADIO_PRO to 480f).map { (on, y) ->
                PDAnnotationWidget().apply {
                    rectangle = PDRectangle(72f, y, 12f, 12f)
                    setPage(page)
                    setParent(field)
                    appearance = toggleAppearance(document, on)
                    markAsWidget()
                }
            }
        field.widgets = widgets
        page.annotations.addAll(widgets)
    }

    private fun addComboBox(
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDComboBox(acroForm)
        field.partialName = CHOICE_NAME
        field.defaultAppearance = DEFAULT_APPEARANCE
        field.setOptions(
            listOf(CHOICE_US, CHOICE_GB),
            listOf(CHOICE_US_LABEL, CHOICE_GB_LABEL),
        )
        attach(acroForm, field, page, PDRectangle(72f, 700f, 120f, 18f))
    }

    private fun addPushButton(
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDPushButton(acroForm)
        field.partialName = BUTTON_NAME
        attach(acroForm, field, page, PDRectangle(72f, 650f, 60f, 20f))
    }

    private fun addReadOnlyField(
        acroForm: PDAcroForm,
        page: PDPage,
    ) {
        val field = PDTextField(acroForm)
        field.partialName = READ_ONLY_NAME
        field.defaultAppearance = DEFAULT_APPEARANCE
        field.isReadOnly = true
        field.cosObject.setString(COSName.V, READ_ONLY_VALUE)
        attach(acroForm, field, page, PDRectangle(72f, 620f, 120f, 18f))
    }

    /**
     * Register [field] on [acroForm] and give its single merged field/widget a
     * rectangle on [page] — the shape PdfBox's own form examples produce.
     */
    private fun attach(
        acroForm: PDAcroForm,
        field: PDTerminalField,
        page: PDPage,
        rect: PDRectangle,
    ): PDAnnotationWidget {
        acroForm.fields.add(field)
        val widget = field.widgets.first()
        widget.rectangle = rect
        widget.setPage(page)
        widget.markAsWidget()
        page.annotations.add(widget)
        return widget
    }

    /** A merged field/widget dictionary still has to declare itself an annotation. */
    private fun PDAnnotationWidget.markAsWidget() {
        cosObject.setItem(COSName.TYPE, COSName.ANNOT)
        cosObject.setName(COSName.SUBTYPE, PDAnnotationWidget.SUB_TYPE)
    }

    /**
     * `/AP /N << /<on> stream /Off stream >>` — the appearance sub-dictionary a
     * tickable widget carries, and the only place a radio button records which
     * choice *this* widget stands for.
     */
    private fun toggleAppearance(
        document: PDDocument,
        onState: String,
    ): PDAppearanceDictionary {
        val states =
            COSDictionary().apply {
                setItem(COSName.getPDFName(onState), blankStream(document))
                setItem(COSName.Off, blankStream(document))
            }
        return PDAppearanceDictionary().apply {
            normalAppearance = PDAppearanceEntry(states)
        }
    }

    private fun blankStream(document: PDDocument): PDAppearanceStream =
        PDAppearanceStream(document).apply {
            bBox = PDRectangle(0f, 0f, 12f, 12f)
            resources = PDResources()
        }

    private const val DEFAULT_APPEARANCE = "/Helv 10 Tf 0 g"
}
