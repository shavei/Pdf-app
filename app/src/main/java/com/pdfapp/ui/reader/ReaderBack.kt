package com.pdfapp.ui.reader

import com.pdfapp.ui.ViewerMode

/** What a system back press should do given the current viewer state. */
enum class ReaderBackAction {
    /** Close the in-document search field and return to plain reading. */
    CLOSE_SEARCH,

    /** Put the fill-form layer away, keeping the values typed into it. */
    CLOSE_FORM,

    /** Leave EDIT mode back to READ (commit any in-progress ink first). */
    EXIT_EDIT,

    /** Bring the immersive (hidden) chrome back before leaving the document. */
    SHOW_CHROME,

    /** Nothing app-specific to unwind: let the system handle back (exit). */
    SYSTEM,
}

/**
 * Pure back-navigation rules (mobile-ui-plan Phase D.3). Back peels the viewer's
 * transient states one layer at a time — search, then form filling, then edit
 * mode, then immersive chrome — before letting the system pop the activity, so
 * predictive back always animates a predictable step. Kept free of Compose so the
 * ordering is unit-testable on the JVM, like [ReaderChrome].
 */
object ReaderBack {
    fun actionFor(
        hasSession: Boolean,
        searchActive: Boolean,
        formActive: Boolean,
        mode: ViewerMode,
        chromeVisible: Boolean,
    ): ReaderBackAction =
        when {
            !hasSession -> ReaderBackAction.SYSTEM
            searchActive -> ReaderBackAction.CLOSE_SEARCH
            // The fill layer is a READ-mode overlay, so it unwinds before the
            // document does — and closing it keeps the values, which is why it
            // ranks above leaving the document rather than warning about them.
            formActive -> ReaderBackAction.CLOSE_FORM
            mode == ViewerMode.EDIT -> ReaderBackAction.EXIT_EDIT
            !chromeVisible -> ReaderBackAction.SHOW_CHROME
            else -> ReaderBackAction.SYSTEM
        }
}
