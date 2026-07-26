package com.pdfapp.ui.document

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pdfapp.core.renderer.form.FormFieldKind
import com.pdfapp.core.renderer.form.PdfFormField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AcroForm fill state (plan Phase 4): the widgets found in the open document,
 * the values the user has typed or ticked, and whether the fill overlay is
 * showing.
 *
 * Edits live here rather than in the PDF: nothing is written until a save, so
 * the source document is never touched and "Reset" is free. Values are keyed by
 * *field* name (not widget), which is both what PdfBox writes back through and
 * what makes a radio group's buttons mutually exclusive for free.
 *
 * Detection runs off [detect], once per document, on IO — the field scan is a
 * PdfBox parse, so a document with no form pays it once and then the fill
 * action simply never appears.
 */
class FormController(
    private val scope: CoroutineScope,
    private val session: () -> DocumentSession?,
    private val onMessage: (String) -> Unit,
) {
    /** Whether the fill overlay is showing over the reader. */
    var active: Boolean by mutableStateOf(false)
        private set

    /** Widgets found in the document; null until [detect] finishes. */
    var fields: List<PdfFormField>? by mutableStateOf(null)
        private set

    /** True when the document carries only an unsupported XFA form. */
    var xfaOnly: Boolean by mutableStateOf(false)
        private set

    /**
     * Bumped by [reset] so the fill layer can key its inputs on it. A text field
     * driven by an external value keeps its own IME buffer while it holds focus,
     * and that buffer syncs back on the next frame — so clearing the model alone
     * would let the just-typed text reappear a moment later. Rebuilding the
     * inputs is what makes Reset actually reset the one the user is typing in.
     */
    var generation: Int by mutableIntStateOf(0)
        private set

    private val edits = mutableStateMapOf<String, String>()

    /** True once the document is known to have fillable fields. */
    val hasForm: Boolean get() = !fields.isNullOrEmpty()

    /** Widgets to draw over each page. */
    val fieldsByPage: Map<Int, List<PdfFormField>> by derivedStateOf {
        fields.orEmpty().groupBy { it.pageIndex }
    }

    /** How many fields the user has changed — drives the save prompt and Reset. */
    val editedCount: Int get() = edits.size

    val isDirty: Boolean get() = edits.isNotEmpty()

    /** The pending edits, ready for `PdfFormWriter.applyValues`. */
    val values: Map<String, String> get() = edits.toMap()

    private var detectJob: Job? = null
    private var detectedFor: DocumentSession? = null

    /**
     * Scan the open document for form fields, at most once per document. Safe to
     * call from a keyed effect or straight after opening.
     */
    fun detect() {
        val open = session() ?: return
        if (detectedFor === open) return
        detectedFor = open
        detectJob?.cancel()
        detectJob =
            scope.launch {
                // The scan is speculative background work on a file we did not
                // write, so a malformed form must not take the app down with an
                // uncaught coroutine failure: an unreadable form is simply a
                // document with nothing to fill, which still reads perfectly.
                val found =
                    runCatching {
                        val document = withContext(Dispatchers.IO) { open.textDocument() }
                        withContext(Dispatchers.IO) { document.formFields() } to document
                    }.getOrNull()
                // A late-returning scan from a document the user has already
                // closed must not populate the new one's chrome.
                if (session() !== open) return@launch
                val (widgets, document) = found ?: (emptyList<PdfFormField>() to null)
                fields = widgets
                if (widgets.isEmpty() && document != null) {
                    xfaOnly =
                        runCatching { withContext(Dispatchers.IO) { document.isXfaOnlyForm() } }
                            .getOrDefault(false)
                    // No fill action can appear for an XFA form, so say why once
                    // rather than leave the user hunting for one (plan Phase 4).
                    if (xfaOnly) onMessage(XFA_MESSAGE)
                }
            }
    }

    /** Reset for a newly opened (or closed) document. */
    fun close() {
        detectJob?.cancel()
        detectedFor = null
        active = false
        fields = null
        xfaOnly = false
        edits.clear()
        generation++
    }

    fun open() {
        if (hasForm) active = true
    }

    fun hide() {
        active = false
    }

    /** Drop every pending edit, returning the fields to the document's own values. */
    fun reset() {
        edits.clear()
        generation++
    }

    /** The value to show for [field]: the user's edit if there is one, else the PDF's. */
    fun valueOf(field: PdfFormField): String = edits[field.name] ?: field.value

    /** Whether [field]'s widget currently reads as ticked/selected. */
    fun isOn(field: PdfFormField): Boolean = field.onValue.isNotEmpty() && valueOf(field) == field.onValue

    /**
     * Record [value] for [field]. Setting a field back to what the document
     * already holds drops the edit, so a typed-then-undone change does not make
     * the document look dirty.
     */
    fun setValue(
        field: PdfFormField,
        value: String,
    ) {
        if (field.readOnly) return
        val trimmed = field.maxLength?.let { value.take(it) } ?: value
        if (trimmed == field.value) edits.remove(field.name) else edits[field.name] = trimmed
    }

    /**
     * Tick or untick a checkbox. Radio buttons go through [select] instead: a
     * group's buttons share one field, so choosing is never a toggle.
     */
    fun toggle(field: PdfFormField) {
        if (field.kind != FormFieldKind.CHECKBOX) return
        setValue(field, if (isOn(field)) "" else field.onValue)
    }

    /** Choose [field]'s widget within its radio group. */
    fun select(field: PdfFormField) {
        if (field.kind != FormFieldKind.RADIO || field.onValue.isEmpty()) return
        setValue(field, field.onValue)
    }

    private companion object {
        const val XFA_MESSAGE = "This is an XFA form, which Signet can't fill"
    }
}
