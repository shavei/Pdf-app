package com.pdfapp.ui.common

import com.google.common.truth.Truth.assertThat
import com.pdfapp.core.renderer.form.FormFieldKind
import com.pdfapp.core.renderer.form.PdfFormField
import com.pdfapp.core.renderer.model.PdfRect
import org.junit.Test

/**
 * Wording for the fill-form layer (plan Phase 4). A form widget shows no text of
 * its own, so these strings are the only thing a screen reader has to go on —
 * which makes them worth pinning down.
 */
class FormSemanticsTest {
    private fun field(
        name: String = "order.billing.zip",
        label: String = "",
        kind: FormFieldKind = FormFieldKind.TEXT,
        readOnly: Boolean = false,
        onValue: String = "",
    ) = PdfFormField(
        name = name,
        label = label,
        widgetIndex = 0,
        kind = kind,
        pageIndex = 0,
        box = PdfRect(0f, 0f, 10f, 10f),
        readOnly = readOnly,
        onValue = onValue,
    )

    @Test
    fun `a text field is named, typed and read out with its value`() {
        val label = FormSemantics.fieldLabel(field(label = "Postcode"), "SW1A 1AA")
        assertThat(label).isEqualTo("Postcode, text field, SW1A 1AA")
    }

    @Test
    fun `an empty text field says so rather than trailing off`() {
        assertThat(FormSemantics.fieldLabel(field(label = "Postcode"), "")).endsWith("empty")
    }

    @Test
    fun `a field with no tooltip falls back to the last segment of its name`() {
        assertThat(FormSemantics.fieldLabel(field(), "1234")).startsWith("zip, ")
    }

    @Test
    fun `a read-only field is announced as read only`() {
        val label = FormSemantics.fieldLabel(field(label = "Reference", readOnly = true), "REF-1")
        assertThat(label).endsWith("read only")
    }

    @Test
    fun `a checkbox announces its tick state`() {
        val box = field(label = "Agree", kind = FormFieldKind.CHECKBOX, onValue = "Yes")
        assertThat(FormSemantics.checkBoxLabel(box, checked = true)).isEqualTo("Agree, checkbox, ticked")
        assertThat(FormSemantics.checkBoxLabel(box, checked = false)).endsWith("not ticked")
    }

    @Test
    fun `a radio button names the choice it stands for`() {
        val button = field(label = "Plan", kind = FormFieldKind.RADIO, onValue = "pro")
        assertThat(FormSemantics.radioLabel(button, selected = true))
            .isEqualTo("Plan, pro, radio button, selected")
    }

    @Test
    fun `an empty dropdown says nothing is selected`() {
        val choice = field(label = "Country", kind = FormFieldKind.CHOICE)
        assertThat(FormSemantics.choiceLabel(choice, "")).isEqualTo("Country, dropdown, nothing selected")
        assertThat(FormSemantics.choiceLabel(choice, "United Kingdom"))
            .isEqualTo("Country, dropdown, United Kingdom")
    }

    @Test
    fun `the status line counts fields before anything is filled`() {
        assertThat(FormSemantics.statusText(fieldCount = 7, editedCount = 0)).isEqualTo("7 fields")
        assertThat(FormSemantics.statusText(fieldCount = 1, editedCount = 0)).isEqualTo("1 field")
    }

    @Test
    fun `the status line switches to progress once a field is filled`() {
        assertThat(FormSemantics.statusText(fieldCount = 7, editedCount = 3)).isEqualTo("3 of 7 filled")
        assertThat(FormSemantics.statusLabel(fieldCount = 7, editedCount = 3))
            .isEqualTo("3 of 7 fields changed")
    }

    @Test
    fun `a form with no fields says so in both registers`() {
        assertThat(FormSemantics.statusText(fieldCount = 0, editedCount = 0)).isEqualTo("No form fields")
        assertThat(FormSemantics.statusLabel(fieldCount = 0, editedCount = 0))
            .isEqualTo("This document has no form fields")
    }

    @Test
    fun `a clean save names how many fields were written and which shape`() {
        assertThat(FormSemantics.saveSummary(applied = 3, skipped = emptyList(), flattened = false))
            .isEqualTo("Saved filled form, 3 fields")
        assertThat(FormSemantics.saveSummary(applied = 1, skipped = emptyList(), flattened = true))
            .isEqualTo("Saved flattened form, 1 field")
    }

    @Test
    fun `a save that could not write a field names it rather than hiding it`() {
        val summary =
            FormSemantics.saveSummary(applied = 2, skipped = listOf("submit", "locked"), flattened = false)
        assertThat(summary).contains("could not write: submit, locked")
    }
}
