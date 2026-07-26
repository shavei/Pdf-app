package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationRail
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pdfapp.ui.PdfEditorViewModel

/**
 * READ-mode side rail (mobile-ui-plan Phase E.2): the same primary actions as
 * [ReaderBottomBar] — they share the [ReaderActions] controls — stood up in a
 * slim start-side column for windows whose short axis is scarce (landscape
 * phones) or wide enough to spare the width (tablets, unfolded foldables). The
 * page keeps the rest of the row, centred.
 *
 * Insets are left at zero: the rail sits inside the Scaffold's content, whose
 * padding already clears the system bars.
 */
@Composable
fun ReaderNavRail(
    viewModel: PdfEditorViewModel,
    onShowThumbnails: () -> Unit,
    onShowGoToPage: () -> Unit,
    onOpenAnother: () -> Unit,
) {
    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
        SearchAction(viewModel)
        ThumbnailsAction(onShowThumbnails)
        NightModeAction(viewModel)
        Spacer(Modifier.weight(1f))
        PageChip(viewModel, onShowGoToPage)
        Spacer(Modifier.weight(1f))
        // Only present for a document that actually has an AcroForm.
        FillFormAction(viewModel)
        EditAction(viewModel)
        OverflowAction(
            viewModel = viewModel,
            onShowGoToPage = onShowGoToPage,
            onOpenAnother = onOpenAnother,
        )
    }
}
