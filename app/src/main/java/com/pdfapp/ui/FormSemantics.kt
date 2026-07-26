package com.pdfapp.ui

import com.pdfapp.core.renderer.form.PdfFormField

/**
 * Screen-reader wording and the visible status line for the fill-form layer
 * (plan Phase 4). Kept free of Compose — like [ReaderSemantics] — so the phrasing
 * is unit-testable on the JVM.
 *
 * Every label has to name the field itself, because a form widget carries no
 * on-screen text of its own: what a sighted user reads is printed on the *page*
 * beside the box, which a screen reader announces as part of the page node, not
 * the input. The name to use is the PDF's tooltip (`/TU`) where the author set
 * one, else the field name — machine-ish, but the only handle there is.
 */
object FormSemantics {
    /** A text field: its name, and either its value or that it is empty. */
    fun fieldLabel(
        field: PdfFormField,
        value: String,
    ): String {
        val name = displayName(field)
        val body = if (value.isBlank()) "empty" else value
        return listOfNotNull(
            "$name, text field, $body",
            "read only".takeIf { field.readOnly },
        ).joinToString(", ")
    }

    /** A checkbox: its name and tick state. */
    fun checkBoxLabel(
        field: PdfFormField,
        checked: Boolean,
    ): String = state(field, "checkbox", if (checked) "ticked" else "not ticked")

    /** One button of a radio group: its export value is the choice it stands for. */
    fun radioLabel(
        field: PdfFormField,
        selected: Boolean,
    ): String {
        val option = field.onValue.ifBlank { "option" }
        val name = displayName(field)
        val body = "$name, $option, radio button, ${if (selected) "selected" else "not selected"}"
        return if (field.readOnly) "$body, read only" else body
    }

    /** A dropdown / list box: its name and the entry currently showing. */
    fun choiceLabel(
        field: PdfFormField,
        shown: String,
    ): String = state(field, "dropdown", shown.ifBlank { "nothing selected" })

    /**
     * The fill bar's status line. Phrased around what the user has changed, since
     * that is what a save will carry — a document's own pre-filled values are
     * already visible in the boxes.
     */
    fun statusText(
        fieldCount: Int,
        editedCount: Int,
    ): String =
        when {
            fieldCount == 0 -> "No form fields"
            editedCount == 0 -> "$fieldCount ${plural(fieldCount, "field")}"
            else -> "$editedCount of $fieldCount filled"
        }

    /** The spoken form of [statusText] — the visible one is terse for a crowded bar. */
    fun statusLabel(
        fieldCount: Int,
        editedCount: Int,
    ): String =
        when {
            fieldCount == 0 -> "This document has no form fields"
            editedCount == 0 -> "Form with $fieldCount ${plural(fieldCount, "field")}, none changed yet"
            else -> "$editedCount of $fieldCount ${plural(fieldCount, "field")} changed"
        }

    /** Snackbar text after a save, naming any field whose value could not be written. */
    fun saveSummary(
        applied: Int,
        skipped: List<String>,
        flattened: Boolean,
    ): String {
        val kind = if (flattened) "flattened form" else "filled form"
        val head = "Saved $kind, $applied ${plural(applied, "field")}"
        return if (skipped.isEmpty()) head else "$head — could not write: ${skipped.joinToString(", ")}"
    }

    private fun displayName(field: PdfFormField): String = field.displayName

    private fun state(
        field: PdfFormField,
        kind: String,
        value: String,
    ): String {
        val body = "${displayName(field)}, $kind, $value"
        return if (field.readOnly) "$body, read only" else body
    }

    private fun plural(
        count: Int,
        noun: String,
    ): String = if (count == 1) noun else "${noun}s"
}
