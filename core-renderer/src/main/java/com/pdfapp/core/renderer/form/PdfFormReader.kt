package com.pdfapp.core.renderer.form

import com.pdfapp.core.renderer.model.PdfRect
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText

/**
 * Reads a document's AcroForm into the flat, Android-free [PdfFormField] list the
 * UI draws inputs from (plan Phase 4). Read-only: writing values back is
 * `:file-persistence`'s job, so this class never mutates the document.
 *
 * The walk is per *widget*, not per field, because that is what gets drawn: one
 * radio group is one field with a button per widget, each with its own rectangle
 * and its own "on" state name. Push buttons and signature fields are skipped —
 * they have no value to fill.
 *
 * XFA-only forms are out of scope by design (see plan Phase 4); [isXfaOnly]
 * reports them so the UI can say so instead of showing an empty form.
 */
internal class PdfFormReader {
    /** True when the document carries a dynamic XFA form and no usable AcroForm fields. */
    fun isXfaOnly(document: PDDocument): Boolean {
        val acroForm = acroFormOf(document) ?: return false
        return acroForm.hasXFA() && acroForm.fields.isEmpty()
    }

    /** Every fillable widget in the document, in field-tree order. Empty when there is no form. */
    fun fields(document: PDDocument): List<PdfFormField> {
        val acroForm = acroFormOf(document) ?: return emptyList()
        val pages = PageLookup(document)
        val out = mutableListOf<PdfFormField>()
        collect(acroForm.fields, depth = 0, pages, out)
        return out
    }

    private fun acroFormOf(document: PDDocument): PDAcroForm? =
        runCatching { document.documentCatalog?.acroForm }.getOrNull()

    private fun collect(
        fields: List<PDField>?,
        depth: Int,
        pages: PageLookup,
        into: MutableList<PdfFormField>,
    ) {
        if (fields == null || depth >= MAX_FIELD_DEPTH) return
        for (field in fields) {
            when (field) {
                // A non-terminal field is a naming node: its children carry the
                // widgets. Recursion is depth-capped against a malformed tree
                // that points back at itself.
                is PDNonTerminalField -> collect(field.children, depth + 1, pages, into)
                is PDTerminalField -> into += widgetsOf(field, pages)
                else -> Unit
            }
        }
    }

    private fun widgetsOf(
        field: PDTerminalField,
        pages: PageLookup,
    ): List<PdfFormField> {
        val kind = kindOf(field) ?: return emptyList()
        val name = field.fullyQualifiedName ?: return emptyList()
        val value = runCatching { field.valueAsString }.getOrNull().orEmpty()
        val widgets = runCatching { field.widgets }.getOrNull() ?: return emptyList()
        return widgets.mapIndexedNotNull { index, widget ->
            // A hidden widget has no rectangle worth drawing over; leaving it out
            // also keeps an invisible input from swallowing taps on the page.
            if (widget.isHidden || widget.isNoView) return@mapIndexedNotNull null
            val box = boxOf(widget) ?: return@mapIndexedNotNull null
            val pageIndex = pages.indexOf(widget) ?: return@mapIndexedNotNull null
            PdfFormField(
                name = name,
                label = runCatching { field.alternateFieldName }.getOrNull().orEmpty(),
                widgetIndex = index,
                kind = kind,
                pageIndex = pageIndex,
                box = box,
                value = value,
                readOnly = field.isReadOnly,
                multiline = field is PDTextField && field.isMultiline,
                maxLength = (field as? PDTextField)?.maxLen?.takeIf { it > 0 },
                options = optionsOf(field),
                onValue = onValueOf(field, widget),
                fontSizePt = fontSizeOf(field),
            )
        }
    }

    private fun kindOf(field: PDTerminalField): FormFieldKind? =
        when {
            field is PDTextField -> FormFieldKind.TEXT
            field is PDCheckBox -> FormFieldKind.CHECKBOX
            field is PDRadioButton -> FormFieldKind.RADIO
            field is PDChoice -> FormFieldKind.CHOICE
            // Push buttons trigger actions and signature fields need Phase 6's
            // cryptographic signing — neither takes a typed-in value.
            else -> null
        }

    /** Widget rectangle in PDF user space, normalised so left < right and bottom < top. */
    private fun boxOf(widget: PDAnnotationWidget): PdfRect? {
        val rect = runCatching { widget.rectangle }.getOrNull() ?: return null
        val box =
            PdfRect(
                left = minOf(rect.lowerLeftX, rect.upperRightX),
                bottom = minOf(rect.lowerLeftY, rect.upperRightY),
                right = maxOf(rect.lowerLeftX, rect.upperRightX),
                top = maxOf(rect.lowerLeftY, rect.upperRightY),
            )
        return box.takeIf { it.width > 0f && it.height > 0f }
    }

    private fun optionsOf(field: PDTerminalField): List<FormOption> {
        val choice = field as? PDChoice ?: return emptyList()
        val values = runCatching { choice.options }.getOrNull().orEmpty()
        val labels = runCatching { choice.optionsDisplayValues }.getOrNull().orEmpty()
        return values.mapIndexed { index, value ->
            FormOption(value = value, label = labels.getOrNull(index)?.ifBlank { value } ?: value)
        }
    }

    /**
     * The state name this widget writes when ticked. A checkbox exposes it
     * directly; a radio button's belongs to the widget, not the group, so it
     * comes from the appearance sub-dictionary — the keys there are the button's
     * states, of which exactly one is not `/Off`.
     */
    private fun onValueOf(
        field: PDTerminalField,
        widget: PDAnnotationWidget,
    ): String =
        when (field) {
            is PDCheckBox -> runCatching { field.onValue }.getOrNull().orEmpty().ifEmpty { DEFAULT_ON_VALUE }
            is PDRadioButton -> appearanceStateOf(widget).orEmpty()
            else -> ""
        }

    private fun appearanceStateOf(widget: PDAnnotationWidget): String? =
        runCatching {
            widget.appearance
                ?.normalAppearance
                ?.takeIf { it.isSubDictionary }
                ?.subDictionary
                ?.keys
                ?.firstOrNull { it != COSName.Off && it != COSName.OFF }
                ?.name
        }.getOrNull()

    /**
     * Font size from the field's default-appearance string (`/Helv 10 Tf 0 g`).
     * PDF's own "size 0" means auto-fit, which is what the UI falls back to when
     * this returns 0 — so an unparsable DA and an auto-sized field behave alike.
     */
    private fun fontSizeOf(field: PDTerminalField): Float {
        val da = runCatching { (field as? PDVariableText)?.defaultAppearance }.getOrNull() ?: return 0f
        val size = FONT_SIZE.find(da)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return 0f
        return size.coerceIn(0f, MAX_FIELD_FONT_SIZE_PT)
    }

    /**
     * Resolves a widget to its page index. [PDAnnotationWidget.getPage] is
     * optional in the spec and often absent, so a miss falls back to a one-time
     * scan of every page's annotations — built lazily, because a form that fills
     * in its `/P` entries never needs it.
     */
    private class PageLookup(private val document: PDDocument) {
        // COSDictionary does not override equals, so the map keys compare by
        // identity — exactly what "is this the same annotation object" needs.
        private val byAnnotation: Map<COSDictionary, Int> by lazy {
            val map = HashMap<COSDictionary, Int>()
            document.pages.forEachIndexed { index, page ->
                runCatching { page.annotations }.getOrNull()?.forEach { annotation ->
                    map[annotation.cosObject] = index
                }
            }
            map
        }

        fun indexOf(widget: PDAnnotationWidget): Int? {
            runCatching { widget.page }.getOrNull()?.let { page ->
                document.pages.indexOf(page).takeIf { it >= 0 }?.let { return it }
            }
            return byAnnotation[widget.cosObject]
        }
    }

    private companion object {
        const val MAX_FIELD_DEPTH = 16
        const val DEFAULT_ON_VALUE = "Yes"
        const val MAX_FIELD_FONT_SIZE_PT = 72f
        val FONT_SIZE = Regex("""([\d.]+)\s+Tf""")
    }
}
