package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.BottomAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pdfapp.ui.PdfEditorViewModel

/**
 * READ-mode bottom bar (mobile-ui-plan Phase B.2): brings the highest-frequency
 * actions down to the thumb — Search, Thumbnails, Night mode and Edit — with a
 * tap-to-jump "page X / N" chip and an overflow for the long tail. The controls
 * themselves live in [ReaderActions], shared with the wide/short-window
 * [ReaderNavRail].
 *
 * Visibility follows the immersive `chromeVisible` state in
 * [com.pdfapp.ui.PdfEditorScreen].
 */
@Composable
fun ReaderBottomBar(
    viewModel: PdfEditorViewModel,
    onShowThumbnails: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    BottomAppBar {
        SearchAction(viewModel)
        ThumbnailsAction(onShowThumbnails)
        NightModeAction(viewModel)
        // The chip carries the weight so the icon buttons keep their 48 dp at
        // any font scale (mobile-ui-plan Phase F.3) — a Row measures fixed
        // children first, so the flexible one absorbs the squeeze. Centring it
        // in the weighted slot keeps the Phase B look.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PageChip(viewModel, onShowGoToPage)
        }
        EditAction(viewModel)
        OverflowAction(
            viewModel = viewModel,
            onShowGoToPage = onShowGoToPage,
            onOpenAnother = onOpenAnother,
        )
    }
}
