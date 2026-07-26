package com.pdfapp.persistence

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField

/**
 * Writes filled AcroForm values back into a PDF with PdfBox (plan Phase 4).
 *
 * Two save shapes, both offered in the UI:
 *  - [applyValues] alone keeps the form **interactive** — the recipient can still
 *    change what was typed, which is what a shared draft wants;
 *  - [applyValues] followed by [flatten] burns the field appearances into the
 *    page content and drops the widgets, so the values can no longer be edited —
 *    the "sign and send" shape, and the one that composes with the overlay
 *    signature from [PdfFlattener].
 *
 * Kept separate from [PdfFlattener] because they touch different layers: the
 * flattener appends to a page's content stream, this mutates the document's
 * interactive form. The caller saves the mutated document via [PdfSaver].
 */
class PdfFormWriter {
    /**
     * Outcome of a write: how many fields took a value, and the names of those
     * that did not. Skips are reported rather than thrown so one hostile field
     * cannot lose the user's whole save — the UI tells them what didn't stick.
     */
    data class Result(
        val applied: Int,
        val skipped: List<String>,
    ) {
        val hasSkips: Boolean get() = skipped.isNotEmpty()
    }

    /**
     * Set [values] — keyed by fully-qualified field name, as read by
     * `PdfFormReader` — on [document]'s AcroForm. Read-only fields and names the
     * form does not have are skipped. Values write in sorted-name order so a
     * given input always produces the same file.
     */
    fun applyValues(
        document: PDDocument,
        values: Map<String, String>,
    ): Result {
        if (values.isEmpty()) return Result(applied = 0, skipped = emptyList())
        val acroForm = acroFormOf(document) ?: return Result(0, values.keys.sorted())
        var applied = 0
        val skipped = mutableListOf<String>()
        for ((name, value) in values.entries.sortedBy { it.key }) {
            val field = runCatching { acroForm.getField(name) }.getOrNull()
            if (field == null || field.isReadOnly || !isFillable(field)) {
                skipped += name
                continue
            }
            runCatching { setValue(field, value) }
                .onSuccess { applied++ }
                .onFailure { skipped += name }
        }
        return Result(applied, skipped)
    }

    /**
     * Burn the form's current appearances into the pages and remove the
     * interactive widgets. No-op when the document has no form.
     *
     * A form whose `/NeedAppearances` flag is set has no appearance streams of
     * its own — the viewer is expected to build them — so they are generated
     * first, otherwise flattening would erase visibly filled fields.
     */
    fun flatten(document: PDDocument) {
        val acroForm = acroFormOf(document) ?: return
        if (acroForm.fields.isEmpty()) return
        if (acroForm.needAppearances) {
            runCatching { acroForm.refreshAppearances() }
            acroForm.setNeedAppearances(false)
        }
        acroForm.flatten()
    }

    /** True when [document] has at least one AcroForm field to fill. */
    fun hasForm(document: PDDocument): Boolean = acroFormOf(document)?.fields?.isNotEmpty() ?: false

    private fun acroFormOf(document: PDDocument): PDAcroForm? =
        runCatching { document.documentCatalog?.acroForm }.getOrNull()

    private fun isFillable(field: PDField): Boolean = field !is PDPushButton && field !is PDSignatureField

    private fun setValue(
        field: PDField,
        value: String,
    ) {
        when (field) {
            // A checkbox stores a state name, not a boolean: an empty (or
            // explicitly "Off") value clears it, anything else ticks it.
            is PDCheckBox -> if (isOff(value)) field.unCheck() else field.check()
            is PDRadioButton -> field.setValue(if (isOff(value)) OFF_STATE else value)
            // Text fields and choices take the string straight. For a
            // multi-select list box that keeps only the chosen entry, which is
            // what the single-choice UI offers.
            else -> field.setValue(value)
        }
    }

    private fun isOff(value: String): Boolean = value.isEmpty() || value.equals(OFF_STATE, ignoreCase = true)

    private companion object {
        const val OFF_STATE = "Off"
    }
}
