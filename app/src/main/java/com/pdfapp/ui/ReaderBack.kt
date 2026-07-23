package com.pdfapp.ui

/** What a system back press should do given the current viewer state. */
enum class ReaderBackAction {
    /** Close the in-document search field and return to plain reading. */
    CLOSE_SEARCH,

    /** Leave EDIT mode back to READ (commit any in-progress ink first). */
    EXIT_EDIT,

    /** Bring the immersive (hidden) chrome back before leaving the document. */
    SHOW_CHROME,

    /** Nothing app-specific to unwind: let the system handle back (exit). */
    SYSTEM,
}

/**
 * Pure back-navigation rules (mobile-ui-plan Phase D.3). Back peels the viewer's
 * transient states one layer at a time — search, then edit mode, then immersive
 * chrome — before letting the system pop the activity, so predictive back always
 * animates a predictable step. Kept free of Compose so the ordering is
 * unit-testable on the JVM, like [ReaderChrome].
 */
object ReaderBack {
    fun actionFor(
        hasSession: Boolean,
        searchActive: Boolean,
        mode: ViewerMode,
        chromeVisible: Boolean,
    ): ReaderBackAction =
        when {
            !hasSession -> ReaderBackAction.SYSTEM
            searchActive -> ReaderBackAction.CLOSE_SEARCH
            mode == ViewerMode.EDIT -> ReaderBackAction.EXIT_EDIT
            !chromeVisible -> ReaderBackAction.SHOW_CHROME
            else -> ReaderBackAction.SYSTEM
        }
}
