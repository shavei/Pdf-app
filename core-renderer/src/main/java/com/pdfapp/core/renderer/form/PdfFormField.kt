package com.pdfapp.core.renderer.form

import com.pdfapp.core.renderer.model.PdfRect

/**
 * The kind of native input an AcroForm field maps to (plan Phase 4). Push
 * buttons and signature fields have no value to fill, so they are not modelled.
 */
enum class FormFieldKind { TEXT, CHECKBOX, RADIO, CHOICE }

/** One selectable entry of a dropdown / list-box field: its stored [value] and its [label]. */
data class FormOption(
    val value: String,
    val label: String,
)

/**
 * One fillable AcroForm **widget**, reduced to what the UI needs to draw a
 * native input over the rendered page and what the writer needs to put the
 * value back.
 *
 * A widget, not a field: a radio group is a single PDF field with one widget per
 * button, and a field may repeat on several pages. Those widgets therefore share
 * a [name] — the fully-qualified field name, which is the write-back key — and
 * are told apart by [widgetIndex]. [box] is in PDF user space, so it maps to the
 * screen through the same [com.pdfapp.core.renderer.model.CoordinateMapper] path
 * as search highlights and link hit-areas.
 */
data class PdfFormField(
    val name: String,
    /**
     * The author's human-readable name for the field (its `/TU` tooltip), empty
     * when the PDF has none. What a screen reader should say — the page's printed
     * caption beside the box belongs to the page, not to the input.
     */
    val label: String,
    val widgetIndex: Int,
    val kind: FormFieldKind,
    val pageIndex: Int,
    val box: PdfRect,
    /** The field's current value as stored in the PDF ("" when unset). */
    val value: String = "",
    val readOnly: Boolean = false,
    val multiline: Boolean = false,
    /** Text-field character limit from `/MaxLen`; null when unlimited. */
    val maxLength: Int? = null,
    /** Choices for [FormFieldKind.CHOICE]; empty for every other kind. */
    val options: List<FormOption> = emptyList(),
    /**
     * The "on" state this particular widget writes when ticked — `/Yes` for a
     * typical checkbox, the button's export value for a radio. Empty for text
     * and choice fields.
     */
    val onValue: String = "",
    /** Font size from the field's default appearance; 0 means auto-size. */
    val fontSizePt: Float = 0f,
) {
    /** Stable per-widget identity for UI keys — [name] alone repeats across widgets. */
    val widgetId: String get() = "$name#$widgetIndex"

    /**
     * Best name to show a user: the author's [label], else the last segment of
     * the fully-qualified [name] — machine-ish, but the only other handle a PDF
     * gives ("order.billing.zip" reads better as "zip").
     */
    val displayName: String get() = label.ifBlank { name.substringAfterLast('.') }.ifBlank { "Field" }

    /** Whether [value] selects this widget (checkbox ticked, radio button chosen). */
    val isOn: Boolean get() = onValue.isNotEmpty() && value == onValue
}
