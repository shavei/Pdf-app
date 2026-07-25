package com.pdfapp.ui

/**
 * Pure visibility rules for the READ-mode chrome (top app bar + bottom bar).
 * Kept free of Compose so the immersive-reading logic (mobile-ui-plan Phase B)
 * is unit-testable on the JVM.
 */
object ReaderChrome {
    /**
     * The top app bar shows whenever the user has chrome up, is searching (the
     * search field lives in the top bar), is editing, or there is no open
     * document (the home/edit chrome is never immersive).
     */
    fun topBarShown(
        chromeVisible: Boolean,
        searchActive: Boolean,
        mode: ViewerMode,
        hasSession: Boolean,
    ): Boolean = chromeVisible || searchActive || mode != ViewerMode.READ || !hasSession

    /**
     * The reader's primary-action surface — the bottom bar, or on wider/shorter
     * windows the side rail and docked pane (mobile-ui-plan Phase E) — only exists
     * while reading an open document with the chrome up; it steps aside for the
     * search field and hides in immersive (chrome-down) reading.
     */
    fun actionsShown(
        chromeVisible: Boolean,
        searchActive: Boolean,
        mode: ViewerMode,
        hasSession: Boolean,
    ): Boolean = hasSession && mode == ViewerMode.READ && !searchActive && chromeVisible
}
