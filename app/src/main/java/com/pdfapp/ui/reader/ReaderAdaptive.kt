package com.pdfapp.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * READ-mode body for the current window size (mobile-ui-plan Phase E).
 *
 * Compact phone portrait gives the whole row to the page — its actions sit in the
 * bottom bar below. Wider or shorter windows put those actions in a start-side
 * [rail] instead (E.2), and expanded widths additionally dock the thumbnail /
 * outline [pane] beside the page rather than overlaying it as a modal sheet
 * (E.3). The page always takes the remaining width.
 *
 * Both side surfaces are part of the reader chrome, so they follow the same
 * immersive [chromeShown] state as the bars — chrome down means a full-bleed page
 * at every window size.
 */
@Composable
fun ReaderBody(
    layout: ReaderLayoutSpec,
    chromeShown: Boolean,
    paneOpen: Boolean,
    rail: @Composable () -> Unit,
    pane: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = chromeShown && layout.navStyle == ReaderNavStyle.SIDE_RAIL,
        ) { rail() }
        AnimatedVisibility(
            visible = chromeShown && layout.docksThumbnails && paneOpen,
        ) { pane() }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}
